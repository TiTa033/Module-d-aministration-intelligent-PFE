package talan.pfe.rulengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.*;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleSetService {

    private final RuleSetRepository ruleSetRepository;
    private final TenantRepository tenantRepository;

    // ─── CREATE ─────────────────────────────────────────────
    @Transactional
    public RuleSetResponse create(CreateRuleSetRequest request, UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        if (ruleSetRepository.existsByNameAndTenantId(
                request.getName(), tenantId)) {
            throw new ConflictException(
                    "A RuleSet with name '" + request.getName() +
                            "' already exists in this tenant");
        }

        RuleSet ruleSet = RuleSet.builder()
                .name(request.getName())
                .description(request.getDescription())
                .evaluationStrategy(request.getEvaluationStrategy())
                .tenant(tenant)
                .build();

        return RuleSetResponse.from(ruleSetRepository.save(ruleSet));
    }

    // ─── GET BY ID ──────────────────────────────────────────
    public RuleSetResponse getById(UUID id, UUID tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);
        return RuleSetResponse.from(ruleSet);
    }

    // ─── GET ALL ────────────────────────────────────────────
    public PageResponse<RuleSetResponse> getAll(
            UUID tenantId,
            String search,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        String searchParam = (search == null) ? "" : search;

        RuleSetStatus ruleSetStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                ruleSetStatus = RuleSetStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                        "Invalid status. Must be DRAFT, ACTIVE or ARCHIVED");
            }
        }

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<RuleSetResponse> resultPage = ruleSetRepository
                .findAllByTenantWithFilters(
                        tenantId, searchParam, ruleSetStatus, pageable)
                .map(RuleSetResponse::from);

        return PageResponse.from(resultPage);
    }

    // ─── UPDATE ─────────────────────────────────────────────
    @Transactional
    public RuleSetResponse update(UUID id, UUID tenantId,
                                  UpdateRuleSetRequest request) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        // Check name uniqueness excluding current
        if (ruleSetRepository.existsByNameAndTenantIdAndIdNot(
                request.getName(), tenantId, id)) {
            throw new ConflictException(
                    "A RuleSet with name '" + request.getName() +
                            "' already exists in this tenant");
        }

        // Cannot update an ARCHIVED ruleset
        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot update an archived RuleSet");
        }

        ruleSet.setName(request.getName());
        ruleSet.setDescription(request.getDescription());
        ruleSet.setEvaluationStrategy(request.getEvaluationStrategy());

        return RuleSetResponse.from(ruleSetRepository.save(ruleSet));
    }

    // ─── ACTIVATE ───────────────────────────────────────────
    @Transactional
    public RuleSetResponse activate(UUID id, UUID tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BadRequestException("RuleSet is already active");
        }

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot activate an archived RuleSet");
        }

        if (ruleSet.getRules().isEmpty()) {
            throw new BadRequestException(
                    "Cannot activate a RuleSet with no rules");
        }

        ruleSet.setStatus(RuleSetStatus.ACTIVE);
        return RuleSetResponse.from(ruleSetRepository.save(ruleSet));
    }

    // ─── ARCHIVE ────────────────────────────────────────────
    @Transactional
    public RuleSetResponse archive(UUID id, UUID tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("RuleSet is already archived");
        }

        ruleSet.setStatus(RuleSetStatus.ARCHIVED);
        return RuleSetResponse.from(ruleSetRepository.save(ruleSet));
    }

    // ─── DRAFT ──────────────────────────────────────────────
    @Transactional
    public RuleSetResponse moveToDraft(UUID id, UUID tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.DRAFT) {
            throw new BadRequestException("RuleSet is already in draft");
        }

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot move an archived RuleSet back to draft");
        }

        ruleSet.setStatus(RuleSetStatus.DRAFT);
        return RuleSetResponse.from(ruleSetRepository.save(ruleSet));
    }

    // ─── DELETE ─────────────────────────────────────────────
    @Transactional
    public void delete(UUID id, UUID tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot delete an active RuleSet. Archive it first.");
        }

        ruleSetRepository.delete(ruleSet);
    }

    // ─── PRIVATE HELPER ─────────────────────────────────────
    private RuleSet findOrThrow(UUID id, UUID tenantId) {
        return ruleSetRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + id));
    }
}