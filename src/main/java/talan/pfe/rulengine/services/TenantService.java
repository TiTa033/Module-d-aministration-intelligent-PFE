package talan.pfe.rulengine.services;


import lombok.RequiredArgsConstructor;
import talan.pfe.rulengine.exception.BadRequestException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.PageResponse;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.exception.*;
import talan.pfe.rulengine.repositories.TenantRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;

    // ─── CREATE ─────────────────────────────────────────────
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

        return TenantResponse.from(tenantRepository.save(tenant));
    }

    // ─── GET BY ID ──────────────────────────────────────────
    public TenantResponse getById(UUID id) {
        Tenant tenant = findOrThrow(id);
        long totalUsers = tenantRepository.countUsersByTenantId(id);
        return TenantResponse.from(tenant, totalUsers);
    }

    // ─── GET ALL (paginated + filterable) ───────────────────
    public PageResponse<TenantResponse> getAll(
            String search,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        TenantStatus tenantStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                tenantStatus = TenantStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                        "Invalid status value. Must be ACTIVE or INACTIVE");
            }
        }

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TenantResponse> tenantPage = tenantRepository
                .findAllWithFilters(search, tenantStatus, pageable)
                .map(tenant -> {
                    long totalUsers =
                            tenantRepository.countUsersByTenantId(tenant.getId());
                    return TenantResponse.from(tenant, totalUsers);
                });

        return PageResponse.from(tenantPage);
    }

    // ─── UPDATE ─────────────────────────────────────────────
    @Transactional
    public TenantResponse update(UUID id, UpdateTenantRequest request) {
        Tenant tenant = findOrThrow(id);

        tenant.setName(request.getName());

        return TenantResponse.from(tenantRepository.save(tenant));
    }

    // ─── ACTIVATE ───────────────────────────────────────────
    @Transactional
    public TenantResponse activate(UUID id) {
        Tenant tenant = findOrThrow(id);

        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            throw new BadRequestException(
                    "Tenant is already active");
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    // ─── DEACTIVATE ─────────────────────────────────────────
    @Transactional
    public TenantResponse deactivate(UUID id) {
        Tenant tenant = findOrThrow(id);

        if (tenant.getStatus() == TenantStatus.INACTIVE) {
            throw new BadRequestException(
                    "Tenant is already inactive");
        }

        tenant.setStatus(TenantStatus.INACTIVE);
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    // ─── DELETE ─────────────────────────────────────────────
    @Transactional
    public void delete(UUID id) {
        Tenant tenant = findOrThrow(id);

        long totalUsers = tenantRepository.countUsersByTenantId(id);
        if (totalUsers > 0) {
            throw new BadRequestException(
                    "Cannot delete tenant with " + totalUsers + " active users. " +
                            "Deactivate all users first.");
        }

        tenantRepository.delete(tenant);
    }

    // ─── PRIVATE HELPER ─────────────────────────────────────
    private Tenant findOrThrow(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + id));
    }
}