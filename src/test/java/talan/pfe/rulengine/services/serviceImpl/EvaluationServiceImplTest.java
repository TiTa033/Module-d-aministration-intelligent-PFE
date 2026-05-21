package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    private EvaluateRequest buildRequest(String inputJson) {
        try {
            JsonNode input = objectMapper.readTree(inputJson);
            EvaluateRequest req = new EvaluateRequest();
            req.setInput(input);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private void stubApiKeyAndRules(Rule... rules) {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(10L))
                .thenReturn(List.of(rules));
    }

    private void stubSaveAndNotify() {
        when(evaluationRequestRepository.save(any())).thenReturn(savedEvalRequest());
        doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());
    }

    // ─── API KEY NOT FOUND ───────────────────────────────────────────────────

    @Test
    @DisplayName("should throw ResourceNotFoundException when API key not found")
    void evaluate_apiKeyNotFound_throws() {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.empty());
        EvaluateRequest req = buildRequest("{\"amount\":500}");
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("API key not found");
    }

    // ─── RULESET VALIDATION ──────────────────────────────────────────────────

    @Test
    @DisplayName("should throw BadRequestException when API key has no linked RuleSet")
    void evaluate_noRuleSet_throws() {
        apiKey.setRuleSet(null);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        EvaluateRequest req = buildRequest("{\"amount\":500}");
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not linked to a RuleSet");
    }

    @Test
    @DisplayName("should throw BadRequestException when RuleSet belongs to different tenant")
    void evaluate_wrongTenant_throws() {
        ruleSet.setTenant(Tenant.builder().id(99L).build());
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        EvaluateRequest req = buildRequest("{\"amount\":500}");
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to this tenant");
    }

    @Test
    @DisplayName("should throw BadRequestException when RuleSet is not ACTIVE")
    void evaluate_inactiveRuleSet_throws() {
        ruleSet.setStatus(RuleSetStatus.DRAFT);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        EvaluateRequest req = buildRequest("{\"amount\":500}");
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only ACTIVE RuleSets");
    }

    // ─── INPUT VALIDATION ────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when input is not a JSON object")
    void evaluate_inputNotObject_throws() {
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(apiKey));
        JsonNode nonObject;
        try {
            nonObject = objectMapper.readTree("\"not-an-object\"");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        EvaluateRequest req = new EvaluateRequest();
        req.setInput(nonObject);
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(Exception.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "unknown field, '{\"amount\":500\\,\"unknown\":\"x\"}', Unknown field(s)",
            "missing field, '{}',                                   Missing field(s)",
            "wrong type,    '{\"amount\":\"not-a-number\"}',        Invalid input type(s)"
    })
    @DisplayName("should throw BadRequestException for invalid input")
    void evaluate_invalidInput_throws(String scenario, String inputJson, String expectedMsg) {
        Rule rule = buildRule("amount", Operator.GREATER_THAN,
                "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "OK");
        stubApiKeyAndRules(rule);
        EvaluateRequest req = buildRequest(inputJson);
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(expectedMsg);
    }

    // ─── STRATEGY: FIRST_MATCH ───────────────────────────────────────────────

    @Nested
    @DisplayName("FIRST_MATCH strategy")
    class FirstMatch {

        @Test
        @DisplayName("should return matched rule output with FIRST_MATCH strategy")
        void evaluate_firstMatch_returnsFirstMatchedRule() {
            Rule rule = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            stubApiKeyAndRules(rule);
            stubSaveAndNotify();

            EvaluateResponse response = service.evaluate(buildRequest("{\"amount\":500}"), principal);

            assertThat(response.getRuleSetId()).isEqualTo(10L);
            assertThat(response.getMatchedRules()).isNotNull();
            assertThat(response.getTotalScore()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("should return empty output when no rules match")
        void evaluate_noMatch_returnsEmpty() {
            Rule rule = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            stubApiKeyAndRules(rule);
            stubSaveAndNotify();

            EvaluateResponse response = service.evaluate(buildRequest("{\"amount\":100}"), principal);

            assertThat(response.getTotalScore()).isZero();
        }

        @Test
        @DisplayName("should skip disabled rules")
        void evaluate_disabledRule_skipped() {
            Rule rule = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            rule.setEnabled(false);
            stubApiKeyAndRules(rule);
            stubSaveAndNotify();

            EvaluateResponse response = service.evaluate(buildRequest("{\"amount\":500}"), principal);

            assertThat(response.getTotalScore()).isZero();
        }
    }

    // ─── STRATEGY: ALL_MATCH ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ALL_MATCH strategy")
    class AllMatch {

        @Test
        @DisplayName("should apply all matching rules with ALL_MATCH strategy")
        void evaluate_allMatch_appliesAllRules() {
            ruleSet.setEvaluationStrategy(EvaluationStrategy.ALL_MATCH);

            Rule rule1 = buildRule("amount", Operator.GREATER_THAN,
                    "300", DataType.NUMBER, ActionType.SET_VALUE, "decision", "APPROVED");
            rule1.setId(1L);

            Rule rule2 = buildRule("amount", Operator.GREATER_THAN,
                    "100", DataType.NUMBER, ActionType.SET_VALUE, "category", "HIGH");
            rule2.setId(2L);
            rule2.setScore(5);

            stubApiKeyAndRules(rule1, rule2);
            stubSaveAndNotify();

            EvaluateResponse response = service.evaluate(buildRequest("{\"amount\":500}"), principal);

            assertThat(response.getTotalScore()).isEqualTo(15.0);
        }
    }

    // ─── STRATEGY: SCORE_BASED ───────────────────────────────────────────────

    @Nested
    @DisplayName("SCORE_BASED strategy")
    class ScoreBased {

        @Test
        @DisplayName("should pick highest score rule with SCORE_BASED strategy")
        void evaluate_scoreBased_picksHighestScore() {
            ruleSet.setEvaluationStrategy(EvaluationStrategy.SCORE_BASED);

            Rule rule1 = buildRule("amount", Operator.GREATER_THAN,
                    "100", DataType.NUMBER, ActionType.SET_VALUE, "decision", "LOW");
            rule1.setId(1L);
            rule1.setScore(5);

            Rule rule2 = buildRule("amount", Operator.GREATER_THAN,
                    "200", DataType.NUMBER, ActionType.SET_VALUE, "decision", "HIGH");
            rule2.setId(2L);
            rule2.setScore(20);

            stubApiKeyAndRules(rule1, rule2);
            stubSaveAndNotify();

            EvaluateResponse response = service.evaluate(buildRequest("{\"amount\":500}"), principal);

            assertThat(response.getTotalScore()).isEqualTo(25.0);
        }
    }

    // ─── OR LOGIC OPERATOR ───────────────────────────────────────────────────

    @Test
    @DisplayName("should match rule with OR logic when at least one condition matches")
    void evaluate_orLogic_matchesPartial() {
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

        stubApiKeyAndRules(rule);
        stubSaveAndNotify();

        EvaluateResponse response = service.evaluate(buildRequest("{\"amount\":500}"), principal);

        assertThat(response.getTotalScore()).isEqualTo(10.0);
    }

    // ─── EMPTY CONDITIONS ────────────────────────────────────────────────────

    @Test
    @DisplayName("should handle rule with no conditions")
    void evaluate_noConditions_alwaysMatches() {
        RuleAction action = RuleAction.builder()
                .actionType(ActionType.SET_VALUE)
                .outputKey("result").outputValue("DEFAULT").build();
        Rule rule = Rule.builder()
                .id(1L).name("Default Rule").priority(1).score(0).enabled(true)
                .logicOperator(LogicOperator.AND)
                .conditions(new ArrayList<>())
                .actions(new ArrayList<>(List.of(action))).build();

        stubApiKeyAndRules(rule);
        stubSaveAndNotify();

        EvaluateResponse response = service.evaluate(buildRequest("{}"), principal);

        assertThat(response).isNotNull();
    }

    // ─── BOOLEAN / DATE TYPES ────────────────────────────────────────────────

    @Test
    @DisplayName("should accept boolean type field in input")
    void evaluate_booleanField_accepted() {
        Rule rule = buildRule("active", Operator.EQUALS,
                "true", DataType.BOOLEAN, ActionType.SET_VALUE, "status", "OK");
        stubApiKeyAndRules(rule);
        stubSaveAndNotify();

        EvaluateResponse response = service.evaluate(buildRequest("{\"active\":true}"), principal);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("should accept valid ISO date field in input")
    void evaluate_dateField_accepted() {
        Rule rule = buildRule("dob", Operator.EQUALS,
                "2000-01-01", DataType.DATE, ActionType.SET_VALUE, "age", "adult");
        stubApiKeyAndRules(rule);
        stubSaveAndNotify();

        EvaluateResponse response = service.evaluate(buildRequest("{\"dob\":\"2000-01-01\"}"), principal);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("should reject invalid date string in input")
    void evaluate_invalidDate_throws() {
        Rule rule = buildRule("dob", Operator.EQUALS,
                "2000-01-01", DataType.DATE, ActionType.SET_VALUE, "age", "adult");
        stubApiKeyAndRules(rule);
        EvaluateRequest req = buildRequest("{\"dob\":\"not-a-date\"}");
        assertThatThrownBy(() -> service.evaluate(req, principal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid input type(s)");
    }
}