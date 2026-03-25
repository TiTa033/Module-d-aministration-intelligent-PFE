package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleAction;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleActionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.RuleActionService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleActionServiceImpl implements RuleActionService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final RuleActionRepository ruleActionRepository;

    @Override
    @Transactional
    public RuleActionResponse create(UUID ruleSetId,
                                     UUID ruleId,
                                     UUID tenantId,
                                     RuleActionRequest request) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot modify actions of an archived RuleSet");
        }

        RuleAction action = RuleAction.builder()
                .actionType(request.getActionType())
                .outputKey(request.getOutputKey())
                .outputValue(request.getOutputValue())
                .rule(rule)
                .build();

        return RuleActionResponse.from(ruleActionRepository.save(action));
    }

    @Override
    public List<RuleActionResponse> getAll(UUID ruleSetId,
                                           UUID ruleId,
                                           UUID tenantId) {
        findRuleOrThrow(ruleSetId, ruleId, tenantId);
        return ruleActionRepository.findAllByRuleIdOrderByIdAsc(ruleId)
                .stream()
                .map(RuleActionResponse::from)
                .toList();
    }

    @Override
    public RuleActionResponse getById(UUID ruleSetId,
                                      UUID ruleId,
                                      UUID id,
                                      UUID tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        RuleAction action = ruleActionRepository.findById(id)
                .filter(a -> a.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleAction not found with id: " + id));
        return RuleActionResponse.from(action);
    }

    @Override
    @Transactional
    public RuleActionResponse update(UUID ruleSetId,
                                     UUID ruleId,
                                     UUID id,
                                     UUID tenantId,
                                     RuleActionRequest request) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot modify actions of an archived RuleSet");
        }

        RuleAction action = ruleActionRepository.findById(id)
                .filter(a -> a.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleAction not found with id: " + id));

        action.setActionType(request.getActionType());
        action.setOutputKey(request.getOutputKey());
        action.setOutputValue(request.getOutputValue());

        return RuleActionResponse.from(ruleActionRepository.save(action));
    }

    @Override
    @Transactional
    public void delete(UUID ruleSetId,
                       UUID ruleId,
                       UUID id,
                       UUID tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot delete actions from an archived RuleSet");
        }

        RuleAction action = ruleActionRepository.findById(id)
                .filter(a -> a.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleAction not found with id: " + id));

        ruleActionRepository.delete(action);
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

