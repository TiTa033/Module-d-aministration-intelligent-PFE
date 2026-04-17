package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.NotifType;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.*;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.mappers.RuleMapper;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.RuleService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleServiceImpl implements RuleService {

    private final RuleRepository ruleRepository;
    private final RuleSetRepository ruleSetRepository;
    private final RuleMapper ruleMapper;
    private final AuditProducer auditProducer;
    private final CurrentUserResolver currentUserResolver;
    private final NotificationProducer notificationProducer;

    @Override
    @Transactional
    public RuleResponse create(Long ruleSetId, Long tenantId, CreateRuleRequest request) {
        RuleSet ruleSet = findRuleSetOrThrow(ruleSetId, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("Cannot add rules to an archived RuleSet");
        }

        if (ruleRepository.existsByNameAndRuleSetId(request.getName(), ruleSetId)) {
            throw new ConflictException(
                    "A Rule with name '" + request.getName() +
                            "' already exists in this RuleSet");
        }

        if (ruleRepository.existsByPriorityAndRuleSetId(request.getPriority(), ruleSetId)) {
            throw new ConflictException(
                    "A Rule with priority " + request.getPriority() +
                            " already exists in this RuleSet");
        }

        Rule rule = Rule.builder()
                .name(request.getName())
                .description(request.getDescription())
                .priority(request.getPriority())
                .logicOperator(request.getLogicOperator())
                .score(request.getScore())
                .ruleSet(ruleSet)
                .build();

        RuleResponse saved = ruleMapper.toDto(ruleRepository.save(rule));

        auditProducer.publish(
                AuditAction.RULE_CREATED, "RULE", saved.getId(),
                null, saved.getName(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    public PageResponse<RuleResponse> getAll(
            Long ruleSetId, Long tenantId, RuleFilterRequest filter) {

        findRuleSetOrThrow(ruleSetId, tenantId);

        String searchParam = (filter.getSearch() == null) ? "" : filter.getSearch();

        Boolean enabledParam = null;
        if (filter.getEnabled() != null && !filter.getEnabled().isBlank()) {
            enabledParam = Boolean.parseBoolean(filter.getEnabled());
        }

        Sort sort = filter.getSortDir().equalsIgnoreCase("desc")
                ? Sort.by(filter.getSortBy()).descending()
                : Sort.by(filter.getSortBy()).ascending();

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<RuleResponse> resultPage = ruleRepository
                .findAllByRuleSetWithFilters(ruleSetId, searchParam, enabledParam, pageable)
                .map(ruleMapper::toDto);

        return PageResponse.from(resultPage);
    }

    @Override
    public List<RuleResponse> getAllList(Long ruleSetId, Long tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        return ruleMapper.toDtoList(
                ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(ruleSetId));
    }

    @Override
    public RuleResponse getById(Long ruleSetId, Long id, Long tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        return ruleMapper.toDto(findRuleOrThrow(id, ruleSetId));
    }

    @Override
    @Transactional
    public RuleResponse update(Long ruleSetId, Long id,
                               Long tenantId, UpdateRuleRequest request) {
        RuleSet ruleSet = findRuleSetOrThrow(ruleSetId, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("Cannot update rules in an archived RuleSet");
        }

        Rule rule = findRuleOrThrow(id, ruleSetId);

        if (ruleRepository.existsByNameAndRuleSetIdAndIdNot(
                request.getName(), ruleSetId, id)) {
            throw new ConflictException(
                    "A Rule with name '" + request.getName() +
                            "' already exists in this RuleSet");
        }

        if (ruleRepository.existsByPriorityAndRuleSetIdAndIdNot(
                request.getPriority(), ruleSetId, id)) {
            throw new ConflictException(
                    "A Rule with priority " + request.getPriority() +
                            " already exists in this RuleSet");
        }

        String oldName = rule.getName();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setPriority(request.getPriority());
        rule.setLogicOperator(request.getLogicOperator());
        rule.setScore(request.getScore());

        RuleResponse saved = ruleMapper.toDto(ruleRepository.save(rule));

        auditProducer.publish(
                AuditAction.RULE_UPDATED, "RULE", id,
                oldName, saved.getName(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    @Transactional
    public RuleResponse enable(Long ruleSetId, Long id, Long tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        Rule rule = findRuleOrThrow(id, ruleSetId);

        if (rule.isEnabled() && rule.getPendingEnabled() == null) {
            throw new BadRequestException("Rule is already enabled");
        }

        rule.setPendingEnabled(true);
        rule.setActivationDate(
                LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        RuleResponse saved = ruleMapper.toDto(ruleRepository.save(rule));

        auditProducer.publish(
                AuditAction.RULE_ENABLED, "RULE", id,
                null, null,
                tenantId, currentUserResolver.getCurrentUserId(), null);
        notificationProducer.publish(
                "Règle activée",
                "La règle '" + rule.getName() + "' sera activée demain à minuit",
                NotifType.INFO, tenantId, id, "RULE");

        return saved;
    }

    @Override
    @Transactional
    public RuleResponse disable(Long ruleSetId, Long id, Long tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        Rule rule = findRuleOrThrow(id, ruleSetId);

        if (!rule.isEnabled() && rule.getPendingEnabled() == null) {
            throw new BadRequestException("Rule is already disabled");
        }

        rule.setPendingEnabled(false);
        rule.setActivationDate(
                LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        RuleResponse saved = ruleMapper.toDto(ruleRepository.save(rule));

        auditProducer.publish(
                AuditAction.RULE_DISABLED, "RULE", id,
                null, null,
                tenantId, currentUserResolver.getCurrentUserId(), null);
        notificationProducer.publish(
                "Règle désactivée",
                "La règle '" + rule.getName() + "' sera désactivée demain à minuit",
                NotifType.WARNING, tenantId, id, "RULE");

        return saved;
    }

    @Override
    @Transactional
    public void delete(Long ruleSetId, Long id, Long tenantId) {
        RuleSet ruleSet = findRuleSetOrThrow(ruleSetId, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot delete rules from an archived RuleSet");
        }

        Rule rule = findRuleOrThrow(id, ruleSetId);

        auditProducer.publish(
                AuditAction.RULE_DELETED, "RULE", id,
                rule.getName(), null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        ruleRepository.delete(rule);
    }

    private RuleSet findRuleSetOrThrow(Long ruleSetId, Long tenantId) {
        return ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));
    }

    private Rule findRuleOrThrow(Long id, Long ruleSetId) {
        return ruleRepository.findByIdAndRuleSetId(id, ruleSetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rule not found with id: " + id));
    }
}