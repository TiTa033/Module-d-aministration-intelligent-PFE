package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import talan.pfe.rulengine.dtos.response.AuditLogResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.entites.AuditLog;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.repositories.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock AuditLogRepository auditLogRepository;

    @InjectMocks AuditServiceImpl service;

    private AuditLog auditLog(Long id, Long tenantId, AuditAction action, String entityType) {
        Tenant tenant = Tenant.builder().id(tenantId).name("Acme").build();
        User user = User.builder().id(1L).email("admin@acme.com").build();
        return AuditLog.builder()
                .id(id).action(action).entityType(entityType).entityId(id)
                .oldValue("old").newValue("new").ipAddress("127.0.0.1")
                .timestamp(LocalDateTime.now())
                .tenant(tenant).user(user)
                .build();
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getAll_returnsMappedPageResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = auditLog(1L, 10L, AuditAction.RULE_CREATED, "RULE");

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<AuditLogResponse> result = service.getAll(10L, null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        AuditLogResponse resp = result.getContent().get(0);
        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getAction()).isEqualTo(AuditAction.RULE_CREATED);
        assertThat(resp.getEntityType()).isEqualTo("RULE");
        assertThat(resp.getTenantId()).isEqualTo(10L);
        assertThat(resp.getTenantName()).isEqualTo("Acme");
        assertThat(resp.getUserEmail()).isEqualTo("admin@acme.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_forcesSortByTimestampDesc() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAll(10L, null, null, null, null, pageable);

        verify(auditLogRepository).findAll(any(Specification.class),
                argThat((Pageable p) -> p.getSort().getOrderFor("timestamp") != null &&
                        p.getSort().getOrderFor("timestamp").isDescending()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_returnsEmptyPageWhenNoLogs() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<AuditLogResponse> result = service.getAll(10L, AuditAction.RULE_CREATED, "RULE", null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_mapsNullTenantAndUserCorrectly() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = AuditLog.builder()
                .id(1L).action(AuditAction.RULESET_CREATED).entityType("RULESET")
                .entityId(1L).timestamp(LocalDateTime.now())
                .tenant(null).user(null).build();

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<AuditLogResponse> result = service.getAll(null, null, null, null, null, pageable);
        AuditLogResponse resp = result.getContent().get(0);

        assertThat(resp.getTenantId()).isNull();
        assertThat(resp.getTenantName()).isNull();
        assertThat(resp.getUserId()).isNull();
        assertThat(resp.getUserEmail()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_withDateRangeFiltersPassedThrough() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAll(10L, null, "RULE", from, to, pageable);

        verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}