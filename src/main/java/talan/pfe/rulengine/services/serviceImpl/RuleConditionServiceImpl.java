package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleCondition;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.RuleConditionMapper;
import talan.pfe.rulengine.repositories.RuleConditionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.RuleConditionService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleConditionServiceImpl implements RuleConditionService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final RuleConditionRepository ruleConditionRepository;
    private final RuleConditionMapper ruleConditionMapper;
    private final AuditProducer auditProducer;
    private final CurrentUserResolver currentUserResolver;

    @Override
    @Transactional
    public RuleConditionResponse create(Long ruleSetId, Long ruleId,
                                        Long tenantId, RuleConditionRequest request) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);

        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot modify conditions of an archived RuleSet");
        }

        RuleCondition condition = RuleCondition.builder()
                .field(request.getField())
                .operator(request.getOperator())
                .value(request.getValue())
                .valueType(request.getValueType())
                .rule(rule)
                .build();

        rule.setPendingUpdate(true);
        rule.setActivationDate(
                LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        RuleConditionResponse saved = ruleConditionMapper.toDto(
                ruleConditionRepository.save(condition));

        auditProducer.publish(
                AuditAction.CONDITION_CREATED, "CONDITION", saved.getId(),
                null, request.getField() + " " + request.getOperator() + " " + request.getValue(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    @Transactional
    public List<RuleConditionResponse> getAll(Long ruleSetId, Long ruleId, Long tenantId) {
        findRuleOrThrow(ruleSetId, ruleId, tenantId);
        return ruleConditionMapper.toDtoList(
                ruleConditionRepository.findAllByRuleIdOrderByIdAsc(ruleId));
    }

    @Override
    public RuleConditionResponse getById(Long ruleSetId, Long ruleId,
                                         Long id, Long tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        RuleCondition condition = ruleConditionRepository.findById(id)
                .filter(c -> c.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleCondition not found with id: " + id));
        return ruleConditionMapper.toDto(condition);
    }

    @Override
    @Transactional
    public RuleConditionResponse update(Long ruleSetId, Long ruleId,
                                        Long id, Long tenantId,
                                        RuleConditionRequest request) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);

        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot modify conditions of an archived RuleSet");
        }

        RuleCondition condition = ruleConditionRepository.findById(id)
                .filter(c -> c.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleCondition not found with id: " + id));

        String oldValue = condition.getField() + " " + condition.getOperator()
                + " " + condition.getValue();

        condition.setField(request.getField());
        condition.setOperator(request.getOperator());
        condition.setValue(request.getValue());
        condition.setValueType(request.getValueType());

        rule.setPendingUpdate(true);
        rule.setActivationDate(
                LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        RuleConditionResponse saved = ruleConditionMapper.toDto(
                ruleConditionRepository.save(condition));

        auditProducer.publish(
                AuditAction.CONDITION_UPDATED, "CONDITION", id,
                oldValue,
                request.getField() + " " + request.getOperator() + " " + request.getValue(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    @Transactional
    public void delete(Long ruleSetId, Long ruleId, Long id, Long tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);

        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot delete conditions from an archived RuleSet");
        }

        RuleCondition condition = ruleConditionRepository.findById(id)
                .filter(c -> c.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleCondition not found with id: " + id));

        rule.setPendingUpdate(true);
        rule.setActivationDate(
                LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        auditProducer.publish(
                AuditAction.CONDITION_DELETED, "CONDITION", id,
                condition.getField() + " " + condition.getOperator()
                        + " " + condition.getValue(),
                null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        ruleConditionRepository.delete(condition);
    }

    private Rule findRuleOrThrow(Long ruleSetId, Long ruleId, Long tenantId) {
        RuleSet ruleSet = ruleSetRepository
                .findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));

        return ruleRepository.findByIdAndRuleSetId(ruleId, ruleSet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rule not found with id: " + ruleId));
    }
}