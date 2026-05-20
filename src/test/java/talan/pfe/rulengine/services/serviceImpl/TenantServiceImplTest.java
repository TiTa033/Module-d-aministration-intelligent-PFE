package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateTenantRequest;
import talan.pfe.rulengine.dtos.request.UpdateTenantRequest;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.TenantMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantServiceImpl")
class TenantServiceImplTest {

    @Mock TenantRepository tenantRepository;
    @Mock TenantMapper tenantMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks TenantServiceImpl service;

    private Tenant tenant;
    private TenantResponse tenantResponse;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder()
                .id(1L).name("BankCorp").slug("bankcorp")
                .status(TenantStatus.ACTIVE).build();
        tenantResponse = TenantResponse.builder()
                .id(1L).name("BankCorp").slug("bankcorp").status("ACTIVE").build();
    }

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("should create tenant when slug is unique")
        void create_success() {
            when(tenantRepository.existsBySlug("bankcorp")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
            when(tenantMapper.toDto(any(Tenant.class))).thenReturn(tenantResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            CreateTenantRequest req = new CreateTenantRequest("BankCorp", "bankcorp", null);
            TenantResponse result = service.create(req);

            assertThat(result.getName()).isEqualTo("BankCorp");
            verify(tenantRepository).save(any(Tenant.class));
        }

        @Test @DisplayName("should throw ConflictException when slug is already taken")
        void create_duplicateSlug_throwsConflict() {
            when(tenantRepository.existsBySlug("bankcorp")).thenReturn(true);
            assertThatThrownBy(() -> service.create(new CreateTenantRequest("BankCorp", "bankcorp", null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("bankcorp");
        }
    }

    @Nested @DisplayName("getById()")
    class GetById {

        @Test @DisplayName("should return tenant with user count")
        void getById_found() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.countUsersByTenantId(1L)).thenReturn(5L);
            when(tenantMapper.toDto(tenant)).thenReturn(tenantResponse);
            assertThat(service.getById(1L).getId()).isEqualTo(1L);
        }

        @Test @DisplayName("should throw ResourceNotFoundException when not found")
        void getById_notFound() {
            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("activate()")
    class Activate {

        @Test @DisplayName("should activate an INACTIVE tenant")
        void activate_success() {
            tenant.setStatus(TenantStatus.INACTIVE);
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any())).thenReturn(tenant);
            when(tenantMapper.toDto(any())).thenReturn(tenantResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            service.activate(1L);
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        }

        @Test @DisplayName("should throw BadRequestException when already ACTIVE")
        void activate_alreadyActive_throwsBadRequest() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            assertThatThrownBy(() -> service.activate(1L))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("already active");
        }
    }

    @Nested @DisplayName("deactivate()")
    class Deactivate {

        @Test @DisplayName("should deactivate an ACTIVE tenant")
        void deactivate_success() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any())).thenReturn(tenant);
            when(tenantMapper.toDto(any())).thenReturn(tenantResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            service.deactivate(1L);
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.INACTIVE);
        }

        @Test @DisplayName("should throw BadRequestException when already INACTIVE")
        void deactivate_alreadyInactive_throwsBadRequest() {
            tenant.setStatus(TenantStatus.INACTIVE);
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            assertThatThrownBy(() -> service.deactivate(1L))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("already inactive");
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {

        @Test @DisplayName("should delete tenant when it has no users")
        void delete_success() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.countUsersByTenantId(1L)).thenReturn(0L);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(tenantRepository).delete(tenant);
            service.delete(1L);
            verify(tenantRepository).delete(tenant);
        }

        @Test @DisplayName("should throw BadRequestException when tenant has active users")
        void delete_withUsers_throwsBadRequest() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.countUsersByTenantId(1L)).thenReturn(3L);
            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("3");
        }
    }

    @Nested @DisplayName("update()")
    class Update {

        @Test @DisplayName("should update tenant name")
        void update_success() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any())).thenReturn(tenant);
            when(tenantMapper.toDto(any())).thenReturn(tenantResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            UpdateTenantRequest req = new UpdateTenantRequest();
            req.setName("NewName");
            assertThat(service.update(1L, req)).isNotNull();
            verify(tenantRepository).save(any());
        }
    }
}
