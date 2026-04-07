package talan.pfe.rulengine.services.serviceImpl;

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
import talan.pfe.rulengine.mappers.RuleSetMapper;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.RuleSetService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleSetServiceImpl implements RuleSetService {

    private final RuleSetRepository ruleSetRepository;
    private final TenantRepository tenantRepository;
    private final RuleSetMapper ruleSetMapper;

    @Override
    @Transactional
    public RuleSetResponse create(CreateRuleSetRequest request,
                                  Long tenantId) {
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

        return ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));
    }

    @Override
    public RuleSetResponse getById(Long id, Long tenantId) {
        return ruleSetMapper.toDto(findOrThrow(id, tenantId));
    }

    public PageResponse<RuleSetResponse> getAll(
            Long tenantId, String search, String status,
            int page, int size, String sortBy, String sortDir) {

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
                .map(ruleSetMapper::toDto);

        return PageResponse.from(resultPage);
    }

    @Override
    @Transactional
    public RuleSetResponse update(Long id, Long tenantId,
                                  UpdateRuleSetRequest request) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSetRepository.existsByNameAndTenantIdAndIdNot(
                request.getName(), tenantId, id)) {
            throw new ConflictException(
                    "A RuleSet with name '" + request.getName() +
                            "' already exists in this tenant");
        }

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot update an archived RuleSet");
        }

        ruleSet.setName(request.getName());
        ruleSet.setDescription(request.getDescription());
        ruleSet.setEvaluationStrategy(request.getEvaluationStrategy());

        return ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));
    }

    @Override
    @Transactional
    public RuleSetResponse activate(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BadRequestException("RuleSet is already active");
        }

        if (ruleSet.getRules().isEmpty()) {
            throw new BadRequestException(
                    "Cannot activate a RuleSet with no rules");
        }

        ruleSet.setStatus(RuleSetStatus.ACTIVE);
        return ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));
    }

    @Override
    @Transactional
    public RuleSetResponse archive(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("RuleSet is already archived");
        }

        ruleSet.setStatus(RuleSetStatus.ARCHIVED);
        return ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));
    }

    @Override
    @Transactional
    public RuleSetResponse moveToDraft(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.DRAFT) {
            throw new BadRequestException("RuleSet is already in draft");
        }

        ruleSet.setStatus(RuleSetStatus.DRAFT);
        return ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));
    }

    @Override
    @Transactional
    public void delete(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot delete an active RuleSet. Archive it first.");
        }

        ruleSetRepository.delete(ruleSet);
    }
    @Override
    @Transactional
    public RuleSetResponse unarchive(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() != RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("Only archived RuleSets can be unarchived");
        }

        ruleSet.setStatus(RuleSetStatus.DRAFT); // or ACTIVE if you prefer
        return ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));
    }

    private RuleSet findOrThrow(Long id, Long tenantId) {
        return ruleSetRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + id));
    }
}