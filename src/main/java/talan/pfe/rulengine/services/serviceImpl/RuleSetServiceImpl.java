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
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.NotifType;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.*;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.mappers.RuleSetMapper;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.RuleSetService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleSetServiceImpl implements RuleSetService {

    private final RuleSetRepository ruleSetRepository;
    private final TenantRepository tenantRepository;
    private final RuleSetMapper ruleSetMapper;
    private final AuditProducer auditProducer;
    private final CurrentUserResolver currentUserResolver;
    private final NotificationProducer notificationProducer;

    @Override
    @Transactional
    public RuleSetResponse create(CreateRuleSetRequest request, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        if (ruleSetRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
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

        RuleSetResponse saved = ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));

        auditProducer.publish(
                AuditAction.RULESET_CREATED, "RULESET", saved.getId(),
                null, saved.getName(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    public RuleSetResponse getById(Long id, Long tenantId) {
        return ruleSetMapper.toDto(findOrThrow(id, tenantId));
    }

    @Override
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
                .findAllByTenantWithFilters(tenantId, searchParam, ruleSetStatus, pageable)
                .map(ruleSetMapper::toDto);

        return PageResponse.from(resultPage);
    }

    @Override
    @Transactional
    public RuleSetResponse update(Long id, Long tenantId, UpdateRuleSetRequest request) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSetRepository.existsByNameAndTenantIdAndIdNot(
                request.getName(), tenantId, id)) {
            throw new ConflictException(
                    "A RuleSet with name '" + request.getName() +
                            "' already exists in this tenant");
        }

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("Cannot update an archived RuleSet");
        }

        String oldName = ruleSet.getName();
        ruleSet.setName(request.getName());
        ruleSet.setDescription(request.getDescription());
        ruleSet.setEvaluationStrategy(request.getEvaluationStrategy());

        RuleSetResponse saved = ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));

        auditProducer.publish(
                AuditAction.RULESET_UPDATED, "RULESET", id,
                oldName, saved.getName(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
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
        RuleSetResponse saved = ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));

        auditProducer.publish(
                AuditAction.RULESET_ACTIVATED, "RULESET", id,
                null, null,
                tenantId, currentUserResolver.getCurrentUserId(), null);
        notificationProducer.publish(
                "RuleSet activé",
                "Le RuleSet '" + ruleSet.getName() + "' est maintenant actif",
                NotifType.SUCCESS, tenantId, id, "RULESET");
        return saved;

    }

    @Override
    @Transactional
    public RuleSetResponse archive(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("RuleSet is already archived");
        }

        ruleSet.setStatus(RuleSetStatus.ARCHIVED);
        RuleSetResponse saved = ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));

        auditProducer.publish(
                AuditAction.RULESET_ARCHIVED, "RULESET", id,
                null, null,
                tenantId, currentUserResolver.getCurrentUserId(), null);
        notificationProducer.publish(
                "RuleSet archivé",
                "Le RuleSet '" + ruleSet.getName() + "' a été archivé",
                NotifType.WARNING, tenantId, id, "RULESET");

        return saved;
    }

    @Override
    @Transactional
    public RuleSetResponse moveToDraft(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.DRAFT) {
            throw new BadRequestException("RuleSet is already in draft");
        }

        ruleSet.setStatus(RuleSetStatus.DRAFT);
        RuleSetResponse saved = ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));

        auditProducer.publish(
                AuditAction.RULESET_MOVED_TO_DRAFT, "RULESET", id,
                null, null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot delete an active RuleSet. Archive it first.");
        }

        auditProducer.publish(
                AuditAction.RULESET_DELETED, "RULESET", id,
                ruleSet.getName(), null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        ruleSetRepository.delete(ruleSet);
    }

    @Override
    @Transactional
    public RuleSetResponse unarchive(Long id, Long tenantId) {
        RuleSet ruleSet = findOrThrow(id, tenantId);

        if (ruleSet.getStatus() != RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("Only archived RuleSets can be unarchived");
        }

        ruleSet.setStatus(RuleSetStatus.DRAFT);
        RuleSetResponse saved = ruleSetMapper.toDto(ruleSetRepository.save(ruleSet));

        auditProducer.publish(
                AuditAction.RULESET_UNARCHIVED, "RULESET", id,
                null, null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    private RuleSet findOrThrow(Long id, Long tenantId) {
        return ruleSetRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + id));
    }
}