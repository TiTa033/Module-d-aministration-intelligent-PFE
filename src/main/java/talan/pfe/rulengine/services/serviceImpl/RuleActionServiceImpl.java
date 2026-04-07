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
import talan.pfe.rulengine.mappers.RuleActionMapper;
import talan.pfe.rulengine.repositories.RuleActionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.RuleActionService;
import talan.pfe.rulengine.services.RuleSetVersioningService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleActionServiceImpl implements RuleActionService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final RuleActionRepository ruleActionRepository;
    private final RuleActionMapper ruleActionMapper;
    private final RuleSetVersioningService ruleSetVersioningService;

    @Override
    @Transactional
    public RuleActionResponse create(Long ruleSetId, Long ruleId,
                                     Long tenantId,
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


        RuleAction saved = ruleActionRepository.save(action);
        ruleSetVersioningService.recordSnapshot(
                ruleSetId, tenantId, "Action added to rule " + rule.getName());
        return ruleActionMapper.toDto(saved);

        rule.setPendingUpdate(true);
        rule.setActivationDate(LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        return ruleActionMapper.toDto(ruleActionRepository.save(action));
    }

    @Override
    public List<RuleActionResponse> getAll(Long ruleSetId, Long ruleId,
                                           Long tenantId) {
        findRuleOrThrow(ruleSetId, ruleId, tenantId);
        return ruleActionMapper.toDtoList(
                ruleActionRepository.findAllByRuleIdOrderByIdAsc(ruleId));
    }

    @Override
    public RuleActionResponse getById(Long ruleSetId, Long ruleId,
                                      Long id, Long tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);
        RuleAction action = ruleActionRepository.findById(id)
                .filter(a -> a.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleAction not found with id: " + id));
        return ruleActionMapper.toDto(action);
    }

    @Override
    @Transactional
    public RuleActionResponse update(Long ruleSetId, Long ruleId,
                                     Long id, Long tenantId,
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

        RuleAction saved = ruleActionRepository.save(action);
        ruleSetVersioningService.recordSnapshot(
                ruleSetId, tenantId, "Action updated on rule " + rule.getName());
        return ruleActionMapper.toDto(saved);

        rule.setPendingUpdate(true);
        rule.setActivationDate(LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        return ruleActionMapper.toDto(ruleActionRepository.save(action));
    }

    @Override
    @Transactional
    public void delete(Long ruleSetId, Long ruleId,
                       Long id, Long tenantId) {
        Rule rule = findRuleOrThrow(ruleSetId, ruleId, tenantId);

        if (rule.getRuleSet().getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot delete actions from an archived RuleSet");
        }

        RuleAction action = ruleActionRepository.findById(id)
                .filter(a -> a.getRule().getId().equals(rule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleAction not found with id: " + id));

        rule.setPendingUpdate(true);
        rule.setActivationDate(LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay());

        ruleActionRepository.delete(action);
        ruleSetVersioningService.recordSnapshot(
                ruleSetId, tenantId, "Action deleted on rule " + rule.getName());
    }

    private Rule findRuleOrThrow(Long ruleSetId, Long ruleId,
                                 Long tenantId) {
        RuleSet ruleSet = ruleSetRepository
                .findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));

        return ruleRepository.findByIdAndRuleSetId(ruleId, ruleSet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rule not found with id: " + ruleId));
    }
}