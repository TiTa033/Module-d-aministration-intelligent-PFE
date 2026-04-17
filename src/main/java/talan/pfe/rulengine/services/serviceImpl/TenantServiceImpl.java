package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.exception.*;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.TenantMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.TenantService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final AuditProducer auditProducer;
    private final CurrentUserResolver currentUserResolver;

    // ─── CREATE ─────────────────────────────────────────────
    @Override
    @Transactional
    public TenantResponse create(CreateTenantRequest request) {
        if (tenantRepository.existsBySlug(request.getSlug())) {
            throw new ConflictException(
                    "Slug '" + request.getSlug() + "' is already taken");
        }

        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .build();

        TenantResponse saved = tenantMapper.toDto(tenantRepository.save(tenant));

        auditProducer.publish(
                AuditAction.TENANT_CREATED, "TENANT", saved.getId(),
                null, saved.getName(),
                null, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    // ─── GET BY ID ──────────────────────────────────────────
    @Override
    public TenantResponse getById(Long id) {
        Tenant tenant = findOrThrow(id);
        long totalUsers = tenantRepository.countUsersByTenantId(id);
        TenantResponse response = tenantMapper.toDto(tenant);
        response.setTotalUsers(totalUsers);
        return response;
    }

    // ─── GET ALL ────────────────────────────────────────────
    @Override
    public PageResponse<TenantResponse> getAll(
            String search, String status,
            int page, int size,
            String sortBy, String sortDir) {

        String searchParam = (search == null) ? "" : search;

        TenantStatus tenantStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                tenantStatus = TenantStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                        "Invalid status. Must be ACTIVE or INACTIVE");
            }
        }

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TenantResponse> tenantPage = tenantRepository
                .findAllWithFilters(searchParam, tenantStatus, pageable)
                .map(tenant -> {
                    long totalUsers =
                            tenantRepository.countUsersByTenantId(tenant.getId());
                    TenantResponse response = tenantMapper.toDto(tenant);
                    response.setTotalUsers(totalUsers);
                    return response;
                });

        return PageResponse.from(tenantPage);
    }

    // ─── UPDATE ─────────────────────────────────────────────
    @Override
    @Transactional
    public TenantResponse update(Long id, UpdateTenantRequest request) {
        Tenant tenant = findOrThrow(id);
        String oldName = tenant.getName();
        tenant.setName(request.getName());
        TenantResponse saved = tenantMapper.toDto(tenantRepository.save(tenant));

        auditProducer.publish(
                AuditAction.TENANT_UPDATED, "TENANT", id,
                oldName, saved.getName(),
                null, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    // ─── ACTIVATE ───────────────────────────────────────────
    @Override
    @Transactional
    public TenantResponse activate(Long id) {
        Tenant tenant = findOrThrow(id);
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            throw new BadRequestException("Tenant is already active");
        }
        tenant.setStatus(TenantStatus.ACTIVE);
        TenantResponse saved = tenantMapper.toDto(tenantRepository.save(tenant));

        auditProducer.publish(
                AuditAction.TENANT_ACTIVATED, "TENANT", id,
                null, null,
                null, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    // ─── DEACTIVATE ─────────────────────────────────────────
    @Override
    @Transactional
    public TenantResponse deactivate(Long id) {
        Tenant tenant = findOrThrow(id);
        if (tenant.getStatus() == TenantStatus.INACTIVE) {
            throw new BadRequestException("Tenant is already inactive");
        }
        tenant.setStatus(TenantStatus.INACTIVE);
        TenantResponse saved = tenantMapper.toDto(tenantRepository.save(tenant));

        auditProducer.publish(
                AuditAction.TENANT_DEACTIVATED, "TENANT", id,
                null, null,
                null, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    // ─── DELETE ─────────────────────────────────────────────
    @Override
    @Transactional
    public void delete(Long id) {
        Tenant tenant = findOrThrow(id);
        long totalUsers = tenantRepository.countUsersByTenantId(id);
        if (totalUsers > 0) {
            throw new BadRequestException(
                    "Cannot delete tenant with " + totalUsers +
                            " active users. Deactivate all users first.");
        }

        auditProducer.publish(
                AuditAction.TENANT_DELETED, "TENANT", id,
                tenant.getName(), null,
                null, currentUserResolver.getCurrentUserId(), null);

        tenantRepository.delete(tenant);
    }

    // ─── PRIVATE HELPERS ────────────────────────────────────
    private Tenant findOrThrow(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + id));
    }
}