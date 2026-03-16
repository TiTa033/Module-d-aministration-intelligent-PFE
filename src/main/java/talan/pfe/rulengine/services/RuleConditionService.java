package talan.pfe.rulengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleCondition;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleConditionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleConditionService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final RuleConditionRepository ruleConditionRepository;

    @Transactional
    public RuleConditionResponse create(UUID ruleSetId,
                                        UUID ruleId,
                                        UUID tenantId,
                                        RuleConditionRequest request) {
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

        return RuleConditionResponse.from(ruleConditionRepository.save(condition));
    }

    public List<RuleConditionResponse> getAll(UUID ruleSetId,
                                              UUID ruleId,
                                              UUID tenantId) {
        findRuleOrThrow(ruleSetId, ruleId, tenantId);
        return ruleConditionRepository.findAllByRuleIdOrderByIdAsc(ruleId)
                .stream()
                .map(RuleConditionResponse::from)
                .toList();
    }

    public RuleConditionResponse getById(UUID ruleSetId,
                                         UUID ruleId,
                                         UUID id,
                                         UUID tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        RuleCondition condition = ruleConditionRepository.findById(id)
                .filter(c -> c.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleCondition not found with id: " + id));
        return RuleConditionResponse.from(condition);
    }

    @Transactional
    public RuleConditionResponse update(UUID ruleSetId,
                                        UUID ruleId,
                                        UUID id,
                                        UUID tenantId,
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

        condition.setField(request.getField());
        condition.setOperator(request.getOperator());
        condition.setValue(request.getValue());
        condition.setValueType(request.getValueType());

        return RuleConditionResponse.from(ruleConditionRepository.save(condition));
    }

    @Transactional
    public void delete(UUID ruleSetId,
                       UUID ruleId,
                       UUID id,
                       UUID tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot delete conditions from an archived RuleSet");
        }

        RuleCondition condition = ruleConditionRepository.findById(id)
                .filter(c -> c.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleCondition not found with id: " + id));

        ruleConditionRepository.delete(condition);
    }

    private Rule findRuleOrThrow(UUID ruleSetId,
                                 UUID ruleId,
                                 UUID tenantId) {
        RuleSet ruleSet = ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));

        return ruleRepository.findByIdAndRuleSetId(ruleId, ruleSet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rule not found with id: " + ruleId));
    }
}

