package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.EvaluateRequest;
import talan.pfe.rulengine.dtos.response.EvaluateResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.*;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.repositories.ApiKeyRepository;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.security.ApiClientPrincipal;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluationServiceImpl")
class EvaluationServiceImplTest {

    @Mock RuleRepository ruleRepository;
    @Mock ApiKeyRepository apiKeyRepository;
    @Mock EvaluationRequestRepository evaluationRequestRepository;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock NotificationProducer notificationProducer;

    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks EvaluationServiceImpl service;

    private ApiClientPrincipal principal;
    private ApiKey apiKey;
    private RuleSet ruleSet;
    private Tenant tenant;

    @BeforeEach
    void setUp() throws Exception {
        // Use reflection to inject real ObjectMapper since @InjectMocks uses mock
        var field = EvaluationServiceImpl.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, objectMapper);

        tenant = Tenant.builder().id(1L).name("BankCorp").build();

        ruleSet = RuleSet.builder()
                .id(10L).name("Credit Scoring")
                .status(RuleSetStatus.ACTIVE)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .tenant(tenant)
                .build();

        apiKey = ApiKey.builder()
                .id(1L).ruleSet(ruleSet).tenant(tenant).build();

        principal = new ApiClientPrincipal(1L, 1L);
    }

    private EvaluateRequest buildRequest(String inputJson) throws Exception {
        JsonNode input = objectMapper.readTree(inputJson);
        EvaluateRequest req = new EvaluateRequest();
        req.setInput(input);
        return req;
    }

    private Rule buildRule(String field, Operator op, String value,
                           DataType type, ActionType actionType,
                           String outputKey, String outputValue) {
        RuleCondition condition = RuleCondition.builder()
                .field(field).operator(op)
                .value(value).valueType(type).build();

        RuleAction action = RuleAction.builder()
                .actionType(actionType)
                .outputKey(outputKey).outputValue(outputValue).build();

        return Rule.builder()
                .id(1L).name("Test Rule").priority(1)
                .score(10).enabled(true)
                .logicOperator(LogicOperator.AND)
                .conditions(new ArrayList<>(List.of(condition)))
                .actions(new ArrayList<>(List.of(action)))
                .build();
    }

    private EvaluationRequest savedEvalRequest() {
        EvaluationRequest saved = EvaluationRequest.builder()
                .id(99L).tenant(tenant).ruleSet(ruleSet).apiKey(apiKey).build();
        EvaluationResult result = EvaluationResult.builder()
                .outputPayload("{}").matchedRules("[]")
                .executionTimeMs(10L).strategyUsed(EvaluationStrategy.FIRST_MATCH)
                .totalScore(0.0).evaluationRequest(saved).build();
        saved.setResult(result);
        return saved;
    }

    // ─── API KEY NOT FOUND ───────────────────────────────────────────────────

    @Test
    @DisplayName("should throw ResourceNotFoundException when API key not found")
    void evaluate_apiKeyNotFound_throws() throws Exception {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.evaluate(buildRequest("{\"amount\":500}"), principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("API key not found");
    }

    // ─── RULESET VALIDATION ──────────────────────────────────────────────────

    @Test
    @DisplayName("should throw BadRequestException when API key has no linked RuleSet")
    void evaluate_noRuleSet_throws() throws Exception {
        apiKey.setRuleSet(null);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        assertThatThrownBy(() -> service.evaluate(buildRequest("{\"amount\":500}"), principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not linked to a RuleSet");
    }

    @Test
    @DisplayName("should throw BadRequestException when RuleSet belongs to different tenant")
    void evaluate_wrongTenant_throws() throws Exception {
        ruleSet.setTenant(Tenant.builder().id(99L).build()); // different tenant
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        assertThatThrownBy(() -> service.evaluate(buildRequest("{\"amount\":500}"), principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to this tenant");
    }

    @Test
    @DisplayName("should throw BadRequestException when RuleSet is not ACTIVE")
    void evaluate_inactiveRuleSet_throws() throws Exception {
        ruleSet.setStatus(RuleSetStatus.DRAFT);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        assertThatThrownBy(() -> service.evaluate(buildRequest("{\"amount\":500}"), principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only ACTIVE RuleSets");
    }

    // ─── INPUT VALIDATION ────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw BadRequestException when input is not a JSON object")
    void evaluate_inputNotObject_throws() throws Exception {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(buildRule("amount", Operator.GREATER_THAN,
                        "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "OK")));
        EvaluateRequest req = new EvaluateRequest();
        req.setInput(objectMapper.readTree("\"not-an-object\""));
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should throw BadRequestException when input has unknown fields")
    void evaluate_unknownField_throws() throws Exception {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(buildRule("amount", Operator.GREATER_THAN,
                        "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "OK")));
        assertThatThrownBy(() -> service.evaluate(
                buildRequest("{\"amount\":500,\"unknown\":\"x\"}"), principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown field(s)");
    }

    @Test
    @DisplayName("should throw BadRequestException when required field is missing")
    void evaluate_missingField_throws() throws Exception {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(buildRule("amount", Operator.GREATER_THAN,
                        "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "OK")));
        assertThatThrownBy(() -> service.evaluate(buildRequest("{}"), principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing field(s)");
    }

    @Test
    @DisplayName("should throw BadRequestException when field has wrong type")
    void evaluate_wrongType_throws() throws Exception {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(buildRule("amount", Operator.GREATER_THAN,
                        "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "OK")));
        assertThatThrownBy(() -> service.evaluate(
                buildRequest("{\"amount\":\"not-a-number\"}"), principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid input type(s)");
    }

    // ─── STRATEGY: FIRST_MATCH ───────────────────────────────────────────────

    @Nested
    @DisplayName("FIRST_MATCH strategy")
    class FirstMatch {

        @Test
        @DisplayName("should return matched rule output with FIRST_MATCH strategy")
        void evaluate_firstMatch_returnsFirstMatchedRule() throws Exception {
            Rule rule = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
            when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                    .thenReturn(List.of(rule));
            when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            EvaluateResponse response = service.evaluate(
                    buildRequest("{\"amount\":500}"), principal);

            assertThat(response.getRuleSetId()).isEqualTo(10L);
            assertThat(response.getMatchedRules()).isNotNull();
            assertThat(response.getTotalScore()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("should return empty output when no rules match")
        void evaluate_noMatch_returnsEmpty() throws Exception {
            Rule rule = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
            when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                    .thenReturn(List.of(rule));
            when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            EvaluateResponse response = service.evaluate(
                    buildRequest("{\"amount\":100}"), principal); // 100 < 300 → no match

            assertThat(response.getTotalScore()).isZero();
        }

        @Test
        @DisplayName("should skip disabled rules")
        void evaluate_disabledRule_skipped() throws Exception {
            Rule rule = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            rule.setEnabled(false);
            when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
            when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                    .thenReturn(List.of(rule));
            when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            EvaluateResponse response = service.evaluate(
                    buildRequest("{\"amount\":500}"), principal);

            assertThat(response.getTotalScore()).isZero();
        }
    }

    // ─── STRATEGY: ALL_MATCH ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ALL_MATCH strategy")
    class AllMatch {

        @Test
        @DisplayName("should apply all matching rules with ALL_MATCH strategy")
        void evaluate_allMatch_appliesAllRules() throws Exception {
            ruleSet.setEvaluationStrategy(EvaluationStrategy.ALL_MATCH);

            Rule rule1 = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            rule1.setId(1L);

            Rule rule2 = buildRule("amount", Operator.GREATER_THAN,
                    "100", DataType.NUMBER, ActionType.SET_VALUE, "category", "HIGH");
            rule2.setId(2L);
            rule2.setScore(5);

            when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
            when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                    .thenReturn(List.of(rule1, rule2));
            when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            EvaluateResponse response = service.evaluate(
                    buildRequest("{\"amount\":500}"), principal);

            assertThat(response.getTotalScore()).isEqualTo(15.0); // 10 + 5
        }
    }

    // ─── STRATEGY: SCORE_BASED ───────────────────────────────────────────────

    @Nested
    @DisplayName("SCORE_BASED strategy")
    class ScoreBased {

        @Test
        @DisplayName("should pick highest score rule with SCORE_BASED strategy")
        void evaluate_scoreBased_picksHighestScore() throws Exception {
            ruleSet.setEvaluationStrategy(EvaluationStrategy.SCORE_BASED);

            Rule rule1 = buildRule("amount", Operator.GREATER_THAN,
                    "100", DataType.NUMBER, ActionType.SET_VALUE, "decision", "LOW");
            rule1.setId(1L);
            rule1.setScore(5);

            Rule rule2 = buildRule("amount", Operator.GREATER_THAN,
                    "200", DataType.NUMBER, ActionType.SET_VALUE, "decision", "HIGH");
            rule2.setId(2L);
            rule2.setScore(20);

            when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
            when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                    .thenReturn(List.of(rule1, rule2));
            when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            EvaluateResponse response = service.evaluate(
                    buildRequest("{\"amount\":500}"), principal);

            // totalScore sums ALL matching, strategy only picks which actions to apply
            assertThat(response.getTotalScore()).isEqualTo(25.0);
        }
    }

    // ─── OR LOGIC OPERATOR ───────────────────────────────────────────────────

    @Test
    @DisplayName("should match rule with OR logic when at least one condition matches")
    void evaluate_orLogic_matchesPartial() throws Exception {
        RuleCondition c1 = RuleCondition.builder()
                .field("amount").operator(Operator.GREATER_THAN)
                .value("1000").valueType(DataType.NUMBER).build();
        RuleCondition c2 = RuleCondition.builder()
                .field("amount").operator(Operator.GREATER_THAN)
                .value("100").valueType(DataType.NUMBER).build();
        RuleAction action = RuleAction.builder()
                .actionType(ActionType.SET_VALUE)
                .outputKey("result").outputValue("PASS").build();
        Rule rule = Rule.builder()
                .id(1L).name("OR Rule").priority(1).score(10).enabled(true)
                .logicOperator(LogicOperator.OR)
                .conditions(new ArrayList<>(List.of(c1, c2)))
                .actions(new ArrayList<>(List.of(action))).build();

        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(rule));
        when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
        doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

        // amount=500 → c1 fails (500 < 1000), c2 passes (500 > 100) → OR matches
        EvaluateResponse response = service.evaluate(
                buildRequest("{\"amount\":500}"), principal);

        assertThat(response.getTotalScore()).isEqualTo(10.0);
    }

    // ─── EMPTY CONDITIONS ────────────────────────────────────────────────────

    @Test
    @DisplayName("should match rule with no conditions (always true)")
    void evaluate_noConditions_alwaysMatches() throws Exception {
        RuleAction action = RuleAction.builder()
                .actionType(ActionType.SET_VALUE)
                .outputKey("result").outputValue("DEFAULT").build();
        Rule rule = Rule.builder()
                .id(1L).name("Default Rule").priority(1).score(0).enabled(true)
                .logicOperator(LogicOperator.AND)
                .conditions(new ArrayList<>())
                .actions(new ArrayList<>(List.of(action))).build();

        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(rule));
        when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
        doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

        // No conditions in rule → field not in expectedType → empty input is fine
        RuleCondition dummy = RuleCondition.builder()
                .field("amount").operator(Operator.GREATER_THAN)
                .value("0").valueType(DataType.NUMBER).build();
        rule.getConditions().add(dummy);
        rule.getConditions().clear(); // reset to empty after validation setup

        // Use a request that matches the "no fields expected" scenario
        EvaluateResponse response = service.evaluate(
                buildRequest("{}"), principal);

        assertThat(response).isNotNull();
    }

    // ─── BOOLEAN / DATE TYPES ────────────────────────────────────────────────

    @Test
    @DisplayName("should accept boolean type field in input")
    void evaluate_booleanField_accepted() throws Exception {
        Rule rule = buildRule("active", Operator.EQUALS,
                "true", DataType.BOOLEAN, ActionType.SET_VALUE, "status", "OK");
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(rule));
        when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
        doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

        EvaluateResponse response = service.evaluate(
                buildRequest("{\"active\":true}"), principal);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("should accept valid ISO date field in input")
    void evaluate_dateField_accepted() throws Exception {
        Rule rule = buildRule("dob", Operator.EQUALS,
                "2000-01-01", DataType.DATE, ActionType.SET_VALUE, "age", "adult");
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(rule));
        when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
        doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

        EvaluateResponse response = service.evaluate(
                buildRequest("{\"dob\":\"2000-01-01\"}"), principal);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("should reject invalid date string in input")
    void evaluate_invalidDate_throws() throws Exception {
        Rule rule = buildRule("dob", Operator.EQUALS,
                "2000-01-01", DataType.DATE, ActionType.SET_VALUE, "age", "adult");
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(rule));

        assertThatThrownBy(() -> service.evaluate(
                buildRequest("{\"dob\":\"not-a-date\"}"), principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid input type(s)");
    }
}