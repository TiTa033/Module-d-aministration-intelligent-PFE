package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import talan.pfe.rulengine.dtos.request.CreateTenantRequest;
import talan.pfe.rulengine.dtos.request.UpdateTenantRequest;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.TenantMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock TenantRepository tenantRepository;
    @Mock TenantMapper tenantMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks TenantServiceImpl tenantService;

    // ─── helpers ─────────────────────────────────────────────

    private Tenant activeTenant(Long id, String name, String slug) {
        return Tenant.builder().id(id).name(name).slug(slug).status(TenantStatus.ACTIVE).build();
    }

    private Tenant inactiveTenant(Long id) {
        return Tenant.builder().id(id).name("T").slug("t").status(TenantStatus.INACTIVE).build();
    }

    private TenantResponse resp(Long id, String name) {
        return TenantResponse.builder().id(id).name(name).slug("s").build();
    }

    private CreateTenantRequest createReq(String name, String slug) {
        CreateTenantRequest req = new CreateTenantRequest();
        req.setName(name); req.setSlug(slug);
        return req;
    }

    private UpdateTenantRequest updateReq(String name) {
        UpdateTenantRequest req = new UpdateTenantRequest();
        req.setName(name);
        return req;
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_whenSlugAlreadyTaken_throwsConflict() {
        when(tenantRepository.existsBySlug("taken-slug")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.create(createReq("Acme", "taken-slug")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("taken-slug");
    }

    @Test
    void create_success_savesAndPublishesAudit() {
        when(tenantRepository.existsBySlug("acme")).thenReturn(false);
        Tenant saved = activeTenant(1L, "Acme", "acme");
        when(tenantRepository.save(any())).thenReturn(saved);
        TenantResponse mappedResp = resp(1L, "Acme");
        when(tenantMapper.toDto(saved)).thenReturn(mappedResp);

        TenantResponse result = tenantService.create(createReq("Acme", "acme"));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Acme");
        verify(auditProducer).publish(eq(AuditAction.TENANT_CREATED), eq("TENANT"),
                eq(1L), isNull(), eq("Acme"), isNull(), any(), any());
    }

    // ─── GET BY ID ────────────────────────────────────────────

    @Test
    void getById_whenNotFound_throwsNotFound() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getById_includesUserCount() {
        Tenant tenant = activeTenant(1L, "Acme", "acme");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.countUsersByTenantId(1L)).thenReturn(7L);
        TenantResponse r = resp(1L, "Acme");
        when(tenantMapper.toDto(tenant)).thenReturn(r);

        TenantResponse result = tenantService.getById(1L);

        assertThat(result.getTotalUsers()).isEqualTo(7L);
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_whenNotFound_throwsNotFound() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.update(99L, updateReq("New")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_success_updatesNameAndPublishesAudit() {
        Tenant tenant = activeTenant(1L, "OldName", "slug");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenReturn(tenant);
        when(tenantMapper.toDto(tenant)).thenReturn(resp(1L, "NewName"));

        TenantResponse result = tenantService.update(1L, updateReq("NewName"));

        assertThat(tenant.getName()).isEqualTo("NewName");
        verify(auditProducer).publish(eq(AuditAction.TENANT_UPDATED), any(), any(),
                eq("OldName"), eq("NewName"), any(), any(), any());
    }

    // ─── ACTIVATE ────────────────────────────────────────────

    @Test
    void activate_whenAlreadyActive_throwsBadRequest() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(activeTenant(1L, "T", "t")));

        assertThatThrownBy(() -> tenantService.activate(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already active");
    }

    @Test
    void activate_whenInactive_setsActiveAndPublishesAudit() {
        Tenant tenant = inactiveTenant(1L);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenReturn(tenant);
        when(tenantMapper.toDto(tenant)).thenReturn(resp(1L, "T"));

        tenantService.activate(1L);

        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(auditProducer).publish(eq(AuditAction.TENANT_ACTIVATED), any(), any(), any(), any(), any(), any(), any());
    }

    // ─── DEACTIVATE ──────────────────────────────────────────

    @Test
    void deactivate_whenAlreadyInactive_throwsBadRequest() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(inactiveTenant(1L)));

        assertThatThrownBy(() -> tenantService.deactivate(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already inactive");
    }

    @Test
    void deactivate_whenActive_setsInactiveAndPublishesAudit() {
        Tenant tenant = activeTenant(1L, "T", "t");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenReturn(tenant);
        when(tenantMapper.toDto(tenant)).thenReturn(resp(1L, "T"));

        tenantService.deactivate(1L);

        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.INACTIVE);
        verify(auditProducer).publish(eq(AuditAction.TENANT_DEACTIVATED), any(), any(), any(), any(), any(), any(), any());
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_whenNotFound_throwsNotFound() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_whenHasActiveUsers_throwsBadRequest() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(activeTenant(1L, "T", "t")));
        when(tenantRepository.countUsersByTenantId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> tenantService.delete(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("3");
    }

    @Test
    void delete_whenNoUsers_deletesAndPublishesAudit() {
        Tenant tenant = activeTenant(1L, "EmptyTenant", "empty");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.countUsersByTenantId(1L)).thenReturn(0L);

        tenantService.delete(1L);

        verify(auditProducer).publish(eq(AuditAction.TENANT_DELETED), any(), any(),
                eq("EmptyTenant"), isNull(), any(), any(), any());
        verify(tenantRepository).delete(tenant);
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_withInvalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> tenantService.getAll("", "INVALID_STATUS", 0, 10, "id", "asc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ACTIVE or INACTIVE");
    }

    @Test
    void getAll_withActiveStatusFilter_queriesCorrectly() {
        Page<Tenant> page = new PageImpl<>(List.of());
        when(tenantRepository.findAllWithFilters(any(), eq(TenantStatus.ACTIVE), any()))
                .thenReturn(page);

        tenantService.getAll("", "ACTIVE", 0, 10, "id", "asc");

        verify(tenantRepository).findAllWithFilters(any(), eq(TenantStatus.ACTIVE), any());
    }

    @Test
    void getAll_withNullStatus_queriesWithNullFilter() {
        Page<Tenant> page = new PageImpl<>(List.of());
        when(tenantRepository.findAllWithFilters(any(), isNull(), any())).thenReturn(page);

        tenantService.getAll(null, null, 0, 10, "name", "asc");

        verify(tenantRepository).findAllWithFilters(any(), isNull(), any());
    }

    @Test
    void getAll_withBlankStatus_queriesWithNullFilter() {
        Page<Tenant> page = new PageImpl<>(List.of());
        when(tenantRepository.findAllWithFilters(any(), isNull(), any())).thenReturn(page);

        tenantService.getAll("acme", "  ", 0, 10, "name", "desc");

        verify(tenantRepository).findAllWithFilters(eq("acme"), isNull(), any());
    }
}
