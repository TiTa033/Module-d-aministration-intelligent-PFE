package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import talan.pfe.rulengine.dtos.rulesetexport.RuleSetExportPackage;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RuleSetSnapshotServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleSetSnapshotServiceImpl service = new RuleSetSnapshotServiceImpl(objectMapper);

    private RuleCondition cond(String field) {
        RuleCondition c = new RuleCondition();
        c.setField(field);
        c.setOperator(Operator.GREATER_THAN);
        c.setValue("100");
        c.setValueType(DataType.NUMBER);
        return c;
    }

    private RuleAction action() {
        RuleAction a = new RuleAction();
        a.setActionType(ActionType.APPROVE);
        a.setOutputKey("result");
        a.setOutputValue("APPROVED");
        return a;
    }

    private Rule rule(String name, int priority) {
        return Rule.builder()
                .name(name).priority(priority).enabled(true)
                .logicOperator(LogicOperator.AND).score(5)
                .conditions(new ArrayList<>(List.of(cond("income"))))
                .actions(new ArrayList<>(List.of(action())))
                .build();
    }

    private RuleSet ruleSet() {
        return RuleSet.builder()
                .id(1L).name("CreditRS").description("Credit scoring")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .status(RuleSetStatus.ACTIVE)
                .currentVersion(3)
                .rules(new ArrayList<>(List.of(rule("Rule B", 2), rule("Rule A", 1))))
                .build();
    }

    // ─── toPackage ────────────────────────────────────────────

    @Test
    void toPackage_mapsBasicFields() {
        RuleSetExportPackage pkg = service.toPackage(ruleSet());

        assertThat(pkg.getName()).isEqualTo("CreditRS");
        assertThat(pkg.getDescription()).isEqualTo("Credit scoring");
        assertThat(pkg.getEvaluationStrategy()).isEqualTo("FIRST_MATCH");
        assertThat(pkg.getStatus()).isEqualTo("ACTIVE");
        assertThat(pkg.getSchemaVersion()).isEqualTo(RuleSetExportPackage.CURRENT_SCHEMA);
    }

    @Test
    void toPackage_rulesAreSortedByPriority() {
        RuleSetExportPackage pkg = service.toPackage(ruleSet());

        assertThat(pkg.getRules()).hasSize(2);
        assertThat(pkg.getRules().get(0).getName()).isEqualTo("Rule A");
        assertThat(pkg.getRules().get(1).getName()).isEqualTo("Rule B");
    }

    @Test
    void toPackage_conditionsAndActionsAreMapped() {
        RuleSetExportPackage pkg = service.toPackage(ruleSet());

        assertThat(pkg.getRules().get(0).getConditions()).hasSize(1);
        assertThat(pkg.getRules().get(0).getConditions().get(0).getField()).isEqualTo("income");
        assertThat(pkg.getRules().get(0).getActions()).hasSize(1);
        assertThat(pkg.getRules().get(0).getActions().get(0).getActionType()).isEqualTo("APPROVE");
    }

    @Test
    void toPackage_emptyRules_returnsEmptyList() {
        RuleSet rs = RuleSet.builder()
                .id(2L).name("Empty").evaluationStrategy(EvaluationStrategy.ALL_MATCH)
                .status(RuleSetStatus.DRAFT)
                .build();

        RuleSetExportPackage pkg = service.toPackage(rs);

        assertThat(pkg.getRules()).isEmpty();
    }

    // ─── toJson ───────────────────────────────────────────────

    @Test
    void toJson_producesValidJsonContainingFields() throws JsonProcessingException {
        String json = service.toJson(ruleSet());

        assertThat(json).contains("CreditRS").contains("FIRST_MATCH").contains("income");
    }

    // ─── parse(String) ────────────────────────────────────────

    @Test
    void parseString_roundTrip() throws JsonProcessingException {
        String json = service.toJson(ruleSet());
        RuleSetExportPackage parsed = service.parse(json);

        assertThat(parsed.getName()).isEqualTo("CreditRS");
        assertThat(parsed.getRules()).hasSize(2);
    }

    // ─── parse(JsonNode) ──────────────────────────────────────

    @Test
    void parseJsonNode_roundTrip() throws JsonProcessingException {
        String json = service.toJson(ruleSet());
        JsonNode node = objectMapper.readTree(json);
        RuleSetExportPackage parsed = service.parse(node);

        assertThat(parsed.getName()).isEqualTo("CreditRS");
        assertThat(parsed.getRules()).hasSize(2);
    }

    @Test
    void parseJsonNode_emptyNode_returnsEmptyPackage() {
        JsonNode emptyNode = objectMapper.createObjectNode();
        RuleSetExportPackage parsed = service.parse(emptyNode);

        assertThat(parsed.getName()).isNull();
    }

    // ─── applyPackage ─────────────────────────────────────────

    @Test
    void applyPackage_appliesNameDescriptionAndStrategy() throws JsonProcessingException {
        RuleSet target = RuleSet.builder()
                .id(2L).name("OldName")
                .status(RuleSetStatus.DRAFT)
                .evaluationStrategy(EvaluationStrategy.ALL_MATCH)
                .rules(new ArrayList<>())
                .build();

        service.applyPackage(target, service.toPackage(ruleSet()));

        assertThat(target.getName()).isEqualTo("CreditRS");
        assertThat(target.getDescription()).isEqualTo("Credit scoring");
        assertThat(target.getEvaluationStrategy()).isEqualTo(EvaluationStrategy.FIRST_MATCH);
        assertThat(target.getStatus()).isEqualTo(RuleSetStatus.ACTIVE);
    }

    @Test
    void applyPackage_rebuildsRulesWithConditionsAndActions() throws JsonProcessingException {
        RuleSet target = RuleSet.builder()
                .id(2L).name("OldName")
                .status(RuleSetStatus.DRAFT)
                .evaluationStrategy(EvaluationStrategy.ALL_MATCH)
                .rules(new ArrayList<>())
                .build();

        service.applyPackage(target, service.toPackage(ruleSet()));

        assertThat(target.getRules()).hasSize(2);
        assertThat(target.getRules().get(0).getConditions()).hasSize(1);
        assertThat(target.getRules().get(0).getActions()).hasSize(1);
        assertThat(target.getRules().get(0).getRuleSet()).isEqualTo(target);
    }

    @Test
    void applyPackage_withBlankStatus_doesNotOverrideStatus() {
        RuleSet target = RuleSet.builder()
                .id(3L).name("OldName")
                .status(RuleSetStatus.DRAFT)
                .evaluationStrategy(EvaluationStrategy.ALL_MATCH)
                .rules(new ArrayList<>())
                .build();

        RuleSetExportPackage pkg = RuleSetExportPackage.builder()
                .name("NewName").evaluationStrategy("FIRST_MATCH")
                .status("").rules(new ArrayList<>())
                .build();
        service.applyPackage(target, pkg);

        assertThat(target.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
    }

    @Test
    void applyPackage_withNullStatus_doesNotOverrideStatus() {
        RuleSet target = RuleSet.builder()
                .id(4L).name("OldName")
                .status(RuleSetStatus.DRAFT)
                .evaluationStrategy(EvaluationStrategy.ALL_MATCH)
                .rules(new ArrayList<>())
                .build();

        RuleSetExportPackage pkg = RuleSetExportPackage.builder()
                .name("NewName").evaluationStrategy("FIRST_MATCH")
                .status(null).rules(new ArrayList<>())
                .build();
        service.applyPackage(target, pkg);

        assertThat(target.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
    }
}
