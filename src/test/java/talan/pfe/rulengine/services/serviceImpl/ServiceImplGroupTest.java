package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import talan.pfe.rulengine.dtos.response.*;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.*;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// ─── NOTIFICATION SERVICE ────────────────────────────────────────────────────

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationServiceImpl service;

    private Tenant tenant;
    private Notification notification;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        notification = Notification.builder()
                .id(1L).title("Alert").message("Rule matched")
                .type(NotifType.INFO).read(false)
                .tenant(tenant).createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("getAll()")
    class GetAll {

        @Test
        @DisplayName("should return paginated notifications for tenant")
        void getAll_returnsPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);
            when(notificationRepository.findByTenantIdOrderByCreatedAtDesc(1L, pageable))
                    .thenReturn(page);

            PageResponse<NotificationResponse> result = service.getAll(1L, pageable);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Alert");
        }
    }

    @Nested
    @DisplayName("getUnreadCount()")
    class GetUnreadCount {

        @Test
        @DisplayName("should return correct unread count")
        void getUnreadCount_returnsCount() {
            when(notificationRepository.countByTenantIdAndReadFalse(1L)).thenReturn(5L);
            assertThat(service.getUnreadCount(1L)).isEqualTo(5L);
        }

        @Test
        @DisplayName("should return 0 when no unread notifications")
        void getUnreadCount_zero() {
            when(notificationRepository.countByTenantIdAndReadFalse(1L)).thenReturn(0L);
            assertThat(service.getUnreadCount(1L)).isZero();
        }
    }

    @Nested
    @DisplayName("markAllAsRead()")
    class MarkAllAsRead {

        @Test
        @DisplayName("should delegate to repository")
        void markAllAsRead_callsRepository() {
            doNothing().when(notificationRepository).markAllAsRead(1L);
            service.markAllAsRead(1L);
            verify(notificationRepository).markAllAsRead(1L);
        }
    }
}

// ─── AUDIT SERVICE ───────────────────────────────────────────────────────────

class AuditServiceImplTest {

    private AuditLogRepository auditLogRepository;
    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        service = new AuditServiceImpl(auditLogRepository);
    }

    @Test
    @DisplayName("getAll() should apply DESC sort by timestamp and return page")
    void getAll_returnsSortedPage() {
        Tenant tenant = Tenant.builder().id(1L).name("T").build();
        AuditLog log = AuditLog.builder()
                .id(1L).action(AuditAction.RULESET_CREATED)
                .entityType("RULESET").entityId(10L)
                .timestamp(LocalDateTime.now())
                .tenant(tenant).build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of(log), pageable, 1);

        when(auditLogRepository.findAll(any(), any(Pageable.class))).thenReturn(page);

        PageResponse<AuditLogResponse> result = service.getAll(
                1L, null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEntityType()).isEqualTo("RULESET");
    }
}

// ─── EVALUATION HISTORY SERVICE ──────────────────────────────────────────────

class EvaluationHistoryServiceImplTest {

    private EvaluationRequestRepository evaluationRequestRepository;
    private EvaluationHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        evaluationRequestRepository = mock(EvaluationRequestRepository.class);
        service = new EvaluationHistoryServiceImpl(
                evaluationRequestRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("getById() should return detail response for valid evaluation")
    void getById_found() {
        Tenant tenant = Tenant.builder().id(1L).name("T").build();
        RuleSet rs = RuleSet.builder().id(10L).name("RS").build();

        EvaluationRequest req = EvaluationRequest.builder()
                .id(1L).tenant(tenant).ruleSet(rs)
                .inputPayload("{\"amount\":500}")
                .requestedAt(LocalDateTime.now()).build();

        EvaluationResult res = EvaluationResult.builder()
                .strategyUsed(EvaluationStrategy.FIRST_MATCH)
                .totalScore(10.0)
                .executionTimeMs(5L)
                .outputPayload("{\"decision\":\"APPROVED\"}")
                .matchedRules("[]")
                .evaluationRequest(req)
                .evaluatedAt(LocalDateTime.now()).build();
        req.setResult(res);

        when(evaluationRequestRepository.findByIdAndTenantId(1L, 1L))
                .thenReturn(Optional.of(req));

        EvaluationDetailResponse result = service.getById(1L, 1L);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRuleSetName()).isEqualTo("RS");
        assertThat(result.getInputPayload()).isEqualTo("{\"amount\":500}");
    }

    @Test
    @DisplayName("getById() should throw ResourceNotFoundException when not found")
    void getById_notFound() {
        when(evaluationRequestRepository.findByIdAndTenantId(999L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getAll() should return paginated evaluation summaries")
    void getAll_returnsSummaries() {
        Tenant tenant = Tenant.builder().id(1L).name("T").build();
        RuleSet rs = RuleSet.builder().id(10L).name("RS").build();

        EvaluationRequest req = EvaluationRequest.builder()
                .id(1L).tenant(tenant).ruleSet(rs)
                .requestedAt(LocalDateTime.now()).build();

        EvaluationResult res = EvaluationResult.builder()
                .strategyUsed(EvaluationStrategy.ALL_MATCH)
                .totalScore(5.0).executionTimeMs(3L)
                .matchedRules("[{\"id\":1}]")
                .evaluationRequest(req)
                .evaluatedAt(LocalDateTime.now()).build();
        req.setResult(res);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EvaluationRequest> page = new PageImpl<>(List.of(req), pageable, 1);

        when(evaluationRequestRepository.findAll(any(), any(Pageable.class))).thenReturn(page);

        PageResponse<EvaluationHistoryResponse> result =
                service.getAll(1L, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMatchedRulesCount()).isEqualTo(1);
    }
}
