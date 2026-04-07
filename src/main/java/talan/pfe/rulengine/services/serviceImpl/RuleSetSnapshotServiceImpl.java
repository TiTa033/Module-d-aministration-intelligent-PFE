package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.dtos.rulesetexport.*;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.services.RuleSetSnapshotService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleSetSnapshotServiceImpl implements RuleSetSnapshotService {

    private final ObjectMapper objectMapper;

    @Override
    public RuleSetExportPackage toPackage(RuleSet ruleSet) {
        List<Rule> ordered = ruleSet.getRules().stream()
                .sorted(Comparator.comparing(Rule::getPriority))
                .collect(Collectors.toList());
        List<ExportedRuleDto> rules = new ArrayList<>();
        for (Rule r : ordered) {
            r.getConditions().size();
            r.getActions().size();
            List<ExportedConditionDto> conds = r.getConditions().stream()
                    .map(c -> ExportedConditionDto.builder()
                            .field(c.getField())
                            .operator(c.getOperator().name())
                            .value(c.getValue())
                            .valueType(c.getValueType().name())
                            .build())
                    .toList();
            List<ExportedActionDto> acts = r.getActions().stream()
                    .map(a -> ExportedActionDto.builder()
                            .actionType(a.getActionType().name())
                            .outputKey(a.getOutputKey())
                            .outputValue(a.getOutputValue())
                            .build())
                    .toList();
            rules.add(ExportedRuleDto.builder()
                    .name(r.getName())
                    .description(r.getDescription())
                    .priority(r.getPriority())
                    .enabled(r.isEnabled())
                    .logicOperator(r.getLogicOperator().name())
                    .score(r.getScore())
                    .conditions(new ArrayList<>(conds))
                    .actions(new ArrayList<>(acts))
                    .build());
        }
        return RuleSetExportPackage.builder()
                .schemaVersion(RuleSetExportPackage.CURRENT_SCHEMA)
                .name(ruleSet.getName())
                .description(ruleSet.getDescription())
                .evaluationStrategy(ruleSet.getEvaluationStrategy().name())
                .status(ruleSet.getStatus().name())
                .rules(rules)
                .build();
    }

    @Override
    public String toJson(RuleSet ruleSet) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(toPackage(ruleSet));
    }

    @Override
    public RuleSetExportPackage parse(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, RuleSetExportPackage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid export JSON: " + e.getMessage());
        }
    }

    @Override
    public RuleSetExportPackage parse(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, RuleSetExportPackage.class);
    }

    @Override
    public void applyPackage(RuleSet target, RuleSetExportPackage pkg) {
        target.setName(pkg.getName());
        target.setDescription(pkg.getDescription());
        target.setEvaluationStrategy(
                EvaluationStrategy.valueOf(pkg.getEvaluationStrategy()));
        if (pkg.getStatus() != null && !pkg.getStatus().isBlank()) {
            target.setStatus(RuleSetStatus.valueOf(pkg.getStatus()));
        }
        target.getRules().clear();

        for (ExportedRuleDto rd : pkg.getRules()) {
            Rule rule = Rule.builder()
                    .name(rd.getName())
                    .description(rd.getDescription())
                    .priority(rd.getPriority())
                    .enabled(Boolean.TRUE.equals(rd.getEnabled()))
                    .logicOperator(LogicOperator.valueOf(rd.getLogicOperator()))
                    .score(rd.getScore())
                    .ruleSet(target)
                    .build();

            for (ExportedConditionDto cd : rd.getConditions()) {
                RuleCondition condition = RuleCondition.builder()
                        .field(cd.getField())
                        .operator(Operator.valueOf(cd.getOperator()))
                        .value(cd.getValue())
                        .valueType(DataType.valueOf(cd.getValueType()))
                        .rule(rule)
                        .build();
                rule.getConditions().add(condition);
            }
            for (ExportedActionDto ad : rd.getActions()) {
                RuleAction action = RuleAction.builder()
                        .actionType(ActionType.valueOf(ad.getActionType()))
                        .outputKey(ad.getOutputKey())
                        .outputValue(ad.getOutputValue())
                        .rule(rule)
                        .build();
                rule.getActions().add(action);
            }
            target.getRules().add(rule);
        }
    }
}
