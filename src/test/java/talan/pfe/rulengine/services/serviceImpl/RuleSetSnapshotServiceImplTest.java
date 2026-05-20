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

    @Nested
    @DisplayName("toPackage()")
    class ToPackage {

        @Test
        @DisplayName("should serialize RuleSet into export package with correct fields")
        void toPackage_success() {
            RuleSet rs = buildRuleSet();
            RuleSetExportPackage pkg = service.toPackage(rs);

            assertThat(pkg).satisfies(p -> {
                assertThat(p.getName()).isEqualTo("Credit Scoring");
                assertThat(p.getDescription()).isEqualTo("Main scoring ruleset");
                assertThat(p.getEvaluationStrategy()).isEqualTo("FIRST_MATCH");
                assertThat(p.getStatus()).isEqualTo("ACTIVE");
                assertThat(p.getSchemaVersion()).isEqualTo(RuleSetExportPackage.CURRENT_SCHEMA);
                assertThat(p.getRules()).hasSize(1);
            });
        }

        @Test
        @DisplayName("should serialize rule conditions and actions correctly")
        void toPackage_ruleDetails() {
            RuleSet rs = buildRuleSet();
            RuleSetExportPackage pkg = service.toPackage(rs);

            ExportedRuleDto exportedRule = pkg.getRules().get(0);
            assertThat(exportedRule).satisfies(r -> {
                assertThat(r.getName()).isEqualTo("High Amount");
                assertThat(r.getPriority()).isEqualTo(1);
                assertThat(r.getLogicOperator()).isEqualTo("AND");
                assertThat(r.getScore()).isEqualTo(10);
                assertThat(r.getConditions()).hasSize(1);
                assertThat(r.getActions()).hasSize(1);
            });

            ExportedConditionDto cond = exportedRule.getConditions().get(0);
            assertThat(cond).satisfies(c -> {
                assertThat(c.getField()).isEqualTo("amount");
                assertThat(c.getOperator()).isEqualTo("GREATER_THAN");
                assertThat(c.getValue()).isEqualTo("1000");
                assertThat(c.getValueType()).isEqualTo("NUMBER");
            });

            ExportedActionDto act = exportedRule.getActions().get(0);
            assertThat(act).satisfies(a -> {
                assertThat(a.getActionType()).isEqualTo("SET_VALUE");
                assertThat(a.getOutputKey()).isEqualTo("decision");
                assertThat(a.getOutputValue()).isEqualTo("APPROVED");
            });
        }

        @Test
        @DisplayName("should order rules by priority ascending")
        void toPackage_ruleOrderedByPriority() {
            RuleSet rs = buildRuleSet();

            Rule rule2 = Rule.builder()
                    .id(2L).name("Low Amount").priority(2)
                    .logicOperator(LogicOperator.AND)
                    .conditions(new ArrayList<>()).actions(new ArrayList<>()).build();
            rs.getRules().add(0, rule2);

            RuleSetExportPackage pkg = service.toPackage(rs);
            assertThat(pkg.getRules().get(0).getName()).isEqualTo("High Amount");
            assertThat(pkg.getRules().get(1).getName()).isEqualTo("Low Amount");
        }
    }

    @Nested
    @DisplayName("toJson() and parse() round-trip")
    class JsonRoundTrip {

        @Test
        @DisplayName("should serialize and deserialize without data loss")
        void roundTrip_success() throws Exception {
            RuleSet rs = buildRuleSet();
            String json = service.toJson(rs);

            assertThat(json).isNotBlank().contains("Credit Scoring");

            RuleSetExportPackage parsed = service.parse(json);
            assertThat(parsed).satisfies(p -> {
                assertThat(p.getName()).isEqualTo("Credit Scoring");
                assertThat(p.getRules()).hasSize(1);
                assertThat(p.getRules().get(0).getName()).isEqualTo("High Amount");
            });
        }

        @Test
        @DisplayName("should throw for invalid JSON")
        void parse_invalidJson_throws() {
            assertThatThrownBy(() -> service.parse("not-valid-json"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("applyPackage()")
    class ApplyPackage {

        @Test
        @DisplayName("should replace all rules on target RuleSet")
        void applyPackage_replacesRules() {
            RuleSet target = buildRuleSet();
            target.getRules().add(Rule.builder()
                    .id(99L).name("Old Rule").priority(5)
                    .logicOperator(LogicOperator.OR)
                    .conditions(new ArrayList<>()).actions(new ArrayList<>()).build());

            RuleSetExportPackage pkg = service.toPackage(buildRuleSet());
            service.applyPackage(target, pkg);

            assertThat(target.getRules()).satisfies(rules -> {
                assertThat(rules).hasSize(1);
                assertThat(rules.get(0).getName()).isEqualTo("High Amount");
            });
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

            assertThat(target).satisfies(t -> {
                assertThat(t.getName()).isEqualTo("Updated RS");
                assertThat(t.getDescription()).isEqualTo("New desc");
                assertThat(t.getEvaluationStrategy()).isEqualTo(EvaluationStrategy.ALL_MATCH);
                assertThat(t.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
            });
        }
    }
}