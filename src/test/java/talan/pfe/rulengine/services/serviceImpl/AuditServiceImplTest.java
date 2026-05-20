package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.repositories.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditServiceImpl")
class AuditServiceImplTest {

    @Mock AuditLogRepository auditLogRepository;
    @InjectMocks AuditServiceImpl service;

    private Tenant tenant;
    private User user;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        user = User.builder().id(2L).email("admin@bank.com").role(Role.ADMIN).tenant(tenant).build();
        auditLog = AuditLog.builder()
                .id(1L).action(AuditAction.RULESET_CREATED)
                .entityType("RULESET").entityId(10L)
                .oldValue(null).newValue("CreditRS")
                .timestamp(LocalDateTime.now())
                .tenant(tenant).user(user).build();
    }

    @Test
    @DisplayName("getAll() should return paginated audit logs with correct mapping")
    void getAll_returnsMappedLogs() {
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(auditLog)));

        PageResponse<AuditLogResponse> result = service.getAll(
                1L, AuditAction.RULESET_CREATED, "RULESET", null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        AuditLogResponse resp = result.getContent().get(0);
        assertThat(resp.getAction()).isEqualTo(AuditAction.RULESET_CREATED);
        assertThat(resp.getEntityType()).isEqualTo("RULESET");
        assertThat(resp.getNewValue()).isEqualTo("CreditRS");
        assertThat(resp.getUserEmail()).isEqualTo("admin@bank.com");
        assertThat(resp.getTenantName()).isEqualTo("BankCorp");
    }

    @Test
    @DisplayName("getAll() should return empty page when no logs found")
    void getAll_emptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<AuditLogResponse> result = service.getAll(
                1L, null, null, null, null, pageable);
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("getAll() should handle audit log with null user and tenant gracefully")
    void getAll_nullUserAndTenant_doesNotThrow() {
        AuditLog logNoUser = AuditLog.builder()
                .id(2L).action(AuditAction.TENANT_CREATED)
                .entityType("TENANT").entityId(1L)
                .timestamp(LocalDateTime.now())
                .tenant(null).user(null).build();

        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(logNoUser)));

        PageResponse<AuditLogResponse> result = service.getAll(1L, null, null, null, null, pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserEmail()).isNull();
        assertThat(result.getContent().get(0).getTenantName()).isNull();
    }
}
