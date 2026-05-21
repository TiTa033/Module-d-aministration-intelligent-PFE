package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.CreateRuleRequest;
import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.CreateRulesFromSuggestionsResponse;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.dtos.response.SuggestedActionDto;
import talan.pfe.rulengine.dtos.response.SuggestedConditionDto;
import talan.pfe.rulengine.dtos.response.SuggestedRuleDto;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.RuleActionService;
import talan.pfe.rulengine.services.RuleConditionService;
import talan.pfe.rulengine.services.RuleService;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleSuggestionService {

    private final RuleService ruleService;
    private final RuleConditionService ruleConditionService;
    private final RuleActionService ruleActionService;
    private final RuleRepository ruleRepository;
    private final RuleSetRepository ruleSetRepository;

    @Transactional
    public CreateRulesFromSuggestionsResponse createFromSuggestions(
            Long ruleSetId, Long tenantId, List<SuggestedRuleDto> rules) {

        ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet introuvable"));

        int created = 0;
        for (SuggestedRuleDto dto : rules) {
            try {
                created += createOne(ruleSetId, tenantId, dto);
            } catch (Exception e) {
                log.warn("Failed to create suggested rule '{}': {}", dto.getName(), e.getMessage());
            }
        }

        return CreateRulesFromSuggestionsResponse.builder()
                .status("OK")
                .rulesCreated(created)
                .createdAt(LocalDate.now().toString())
                .build();
    }

    private int createOne(Long ruleSetId, Long tenantId, SuggestedRuleDto dto) {
        // Skip if name already used in this RuleSet
        if (ruleRepository.existsByNameAndRuleSetId(dto.getName(), ruleSetId)) {
            log.debug("Skipping duplicate rule name '{}' in ruleSetId={}", dto.getName(), ruleSetId);
            return 0;
        }

        // Find a free priority (auto-bump if taken)
        int priority = dto.getPriority() != null ? dto.getPriority() : 1;
        while (ruleRepository.existsByPriorityAndRuleSetId(priority, ruleSetId)) {
            priority++;
        }

        LogicOperator logicOperator = parseLogicOperator(dto.getLogicOperator());

        CreateRuleRequest req = new CreateRuleRequest();
        req.setName(dto.getName());
        req.setDescription(dto.getDescription());
        req.setPriority(priority);
        req.setLogicOperator(logicOperator);
        req.setScore(dto.getScore());

        RuleResponse created = ruleService.create(ruleSetId, tenantId, req);
        Long ruleId = created.getId();

        // Conditions
        if (dto.getConditions() != null) {
            for (SuggestedConditionDto c : dto.getConditions()) {
                createCondition(ruleSetId, ruleId, tenantId, c);
            }
        }

        // Actions
        if (dto.getActions() != null) {
            for (SuggestedActionDto a : dto.getActions()) {
                createAction(ruleSetId, ruleId, tenantId, a);
            }
        }

        log.info("Created suggested rule '{}' (priority={}) in ruleSetId={}", dto.getName(), priority, ruleSetId);
        return 1;
    }

    private void createCondition(Long ruleSetId, Long ruleId, Long tenantId, SuggestedConditionDto c) {
        try {
            Operator op = Operator.valueOf(c.getOperator().toUpperCase());
            DataType dt = DataType.valueOf(c.getValueType().toUpperCase());
            String value = (c.getValue() != null) ? c.getValue() : "";

            RuleConditionRequest req = new RuleConditionRequest();
            req.setField(c.getField());
            req.setOperator(op);
            req.setValue(value);
            req.setValueType(dt);
            ruleConditionService.create(ruleSetId, ruleId, tenantId, req);
        } catch (Exception e) {
            log.warn("Skipping invalid condition field='{}' op='{}': {}", c.getField(), c.getOperator(), e.getMessage());
        }
    }

    private void createAction(Long ruleSetId, Long ruleId, Long tenantId, SuggestedActionDto a) {
        try {
            ActionType at = ActionType.valueOf(a.getActionType().toUpperCase());
            String outputValue = (a.getOutputValue() != null) ? a.getOutputValue() : "";

            RuleActionRequest req = new RuleActionRequest();
            req.setActionType(at);
            req.setOutputKey(a.getOutputKey());
            req.setOutputValue(outputValue);
            ruleActionService.create(ruleSetId, ruleId, tenantId, req);
        } catch (Exception e) {
            log.warn("Skipping invalid action type='{}': {}", a.getActionType(), e.getMessage());
        }
    }

    private LogicOperator parseLogicOperator(String raw) {
        if (raw == null) return LogicOperator.AND;
        try { return LogicOperator.valueOf(raw.toUpperCase()); }
        catch (Exception e) { return LogicOperator.AND; }
    }
}
