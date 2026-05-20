package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import talan.pfe.rulengine.dtos.rulesetexport.RuleSetExportPackage;
import talan.pfe.rulengine.dtos.rulesetexport.ExportedRuleDto;
import talan.pfe.rulengine.dtos.rulesetexport.ExportedConditionDto;
import talan.pfe.rulengine.dtos.rulesetexport.ExportedActionDto;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RuleSetSnapshotServiceImpl")
class RuleSetSnapshotServiceImplTest {

    private RuleSetSnapshotServiceImpl service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RuleSetSnapshotServiceImpl(objectMapper);
    }

    private RuleSet buildRuleSet() {
        Tenant tenant = Tenant.builder().id(1L).name("Bank").build();

        RuleCondition condition = RuleCondition.builder()
                .field("amount").operator(Operator.GREATER_THAN)
                .value("1000").valueType(DataType.NUMBER).build();

        RuleAction action = RuleAction.builder()
                .actionType(ActionType.SET_VALUE)
                .outputKey("decision").outputValue("APPROVED").build();

        Rule rule = Rule.builder()
                .id(1L).name("High Amount").priority(1)
                .logicOperator(LogicOperator.AND)
                .score(10).enabled(true)
                .conditions(new ArrayList<>(List.of(condition)))
                .actions(new ArrayList<>(List.of(action))).build();

        return RuleSet.builder()
                .id(10L).name("Credit Scoring")
                .description("Main scoring ruleset")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .status(RuleSetStatus.ACTIVE)
                .tenant(tenant)
                .rules(new ArrayList<>(List.of(rule))).build();
    }

    // ─── toPackage() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toPackage()")
    class ToPackage {

        @Test
        @DisplayName("should serialize RuleSet into export package with correct fields")
        void toPackage_success() {
            RuleSet rs = buildRuleSet();
            RuleSetExportPackage pkg = service.toPackage(rs);

            assertThat(pkg.getName()).isEqualTo("Credit Scoring");
            assertThat(pkg.getDescription()).isEqualTo("Main scoring ruleset");
            assertThat(pkg.getEvaluationStrategy()).isEqualTo("FIRST_MATCH");
            assertThat(pkg.getStatus()).isEqualTo("ACTIVE");
            assertThat(pkg.getSchemaVersion()).isEqualTo(RuleSetExportPackage.CURRENT_SCHEMA);
            assertThat(pkg.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("should serialize rule conditions and actions correctly")
        void toPackage_ruleDetails() {
            RuleSet rs = buildRuleSet();
            RuleSetExportPackage pkg = service.toPackage(rs);

            ExportedRuleDto exportedRule = pkg.getRules().get(0);
            assertThat(exportedRule.getName()).isEqualTo("High Amount");
            assertThat(exportedRule.getPriority()).isEqualTo(1);
            assertThat(exportedRule.getLogicOperator()).isEqualTo("AND");
            assertThat(exportedRule.getScore()).isEqualTo(10);
            assertThat(exportedRule.getConditions()).hasSize(1);
            assertThat(exportedRule.getActions()).hasSize(1);

            ExportedConditionDto cond = exportedRule.getConditions().get(0);
            assertThat(cond.getField()).isEqualTo("amount");
            assertThat(cond.getOperator()).isEqualTo("GREATER_THAN");
            assertThat(cond.getValue()).isEqualTo("1000");
            assertThat(cond.getValueType()).isEqualTo("NUMBER");

            ExportedActionDto act = exportedRule.getActions().get(0);
            assertThat(act.getActionType()).isEqualTo("SET_VALUE");
            assertThat(act.getOutputKey()).isEqualTo("decision");
            assertThat(act.getOutputValue()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("should order rules by priority ascending")
        void toPackage_ruleOrderedByPriority() {
            RuleSet rs = buildRuleSet();

            Rule rule2 = Rule.builder()
                    .id(2L).name("Low Amount").priority(2)
                    .logicOperator(LogicOperator.AND)
                    .conditions(new ArrayList<>()).actions(new ArrayList<>()).build();
            rs.getRules().add(0, rule2); // add rule2 first, but priority=2

            RuleSetExportPackage pkg = service.toPackage(rs);
            assertThat(pkg.getRules().get(0).getName()).isEqualTo("High Amount"); // priority=1
            assertThat(pkg.getRules().get(1).getName()).isEqualTo("Low Amount");  // priority=2
        }
    }

    // ─── toJson() / parse() round-trip ───────────────────────────────────────

    @Nested
    @DisplayName("toJson() and parse() round-trip")
    class JsonRoundTrip {

        @Test
        @DisplayName("should serialize and deserialize without data loss")
        void roundTrip_success() throws Exception {
            RuleSet rs = buildRuleSet();
            String json = service.toJson(rs);

            assertThat(json).isNotBlank();
            assertThat(json).contains("Credit Scoring");

            RuleSetExportPackage parsed = service.parse(json);
            assertThat(parsed.getName()).isEqualTo("Credit Scoring");
            assertThat(parsed.getRules()).hasSize(1);
            assertThat(parsed.getRules().get(0).getName()).isEqualTo("High Amount");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid JSON")
        void parse_invalidJson_throwsIllegalArgument() {
            assertThatThrownBy(() -> service.parse("not-valid-json"))
                    .isInstanceOf(Exception.class);
        }
    }

    // ─── applyPackage() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("applyPackage()")
    class ApplyPackage {

        @Test
        @DisplayName("should replace all rules on target RuleSet")
        void applyPackage_replacesRules() {
            RuleSet target = buildRuleSet();
            // Add extra rule to verify clearing
            target.getRules().add(Rule.builder()
                    .id(99L).name("Old Rule").priority(5)
                    .logicOperator(LogicOperator.OR)
                    .conditions(new ArrayList<>()).actions(new ArrayList<>()).build());

            RuleSetExportPackage pkg = service.toPackage(buildRuleSet()); // fresh pkg with 1 rule

            service.applyPackage(target, pkg);

            assertThat(target.getRules()).hasSize(1);
            assertThat(target.getRules().get(0).getName()).isEqualTo("High Amount");
        }

        @Test
        @DisplayName("should update RuleSet metadata from package")
        void applyPackage_updatesMetadata() {
            RuleSet target = buildRuleSet();

            ExportedRuleDto exportedRule = ExportedRuleDto.builder()
                    .name("New Rule").priority(1).enabled(true)
                    .logicOperator("AND").score(5)
                    .conditions(new ArrayList<>()).actions(new ArrayList<>()).build();

            RuleSetExportPackage pkg = RuleSetExportPackage.builder()
                    .schemaVersion(1)
                    .name("Updated RS")
                    .description("New desc")
                    .evaluationStrategy("ALL_MATCH")
                    .status("DRAFT")
                    .rules(List.of(exportedRule))
                    .build();

            service.applyPackage(target, pkg);

            assertThat(target.getName()).isEqualTo("Updated RS");
            assertThat(target.getDescription()).isEqualTo("New desc");
            assertThat(target.getEvaluationStrategy()).isEqualTo(EvaluationStrategy.ALL_MATCH);
            assertThat(target.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
        }
    }
}
