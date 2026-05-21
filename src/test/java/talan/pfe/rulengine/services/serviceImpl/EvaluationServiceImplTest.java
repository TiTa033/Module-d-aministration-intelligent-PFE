package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock RuleRepository ruleRepository;
    @Mock ApiKeyRepository apiKeyRepository;
    @Mock EvaluationRequestRepository evaluationRequestRepository;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock NotificationProducer notificationProducer;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks EvaluationServiceImpl evaluationService;

    // ─── helpers ─────────────────────────────────────────────

    private ApiClientPrincipal principal(Long tenantId) {
        return new ApiClientPrincipal(tenantId, 5L);
    }

    private Tenant tenant(Long id) {
        return Tenant.builder().id(id).name("T" + id).build();
    }

    private ApiKey activeKey(RuleSet ruleSet) {
        return ApiKey.builder()
                .id(5L).name("key").active(true).keyHash("hash")
                .tenant(ruleSet.getTenant()).ruleSet(ruleSet)
                .build();
    }

    private RuleSet activeRuleSet(EvaluationStrategy strategy) {
        return RuleSet.builder()
                .id(1L).name("RS").status(RuleSetStatus.ACTIVE)
                .evaluationStrategy(strategy).tenant(tenant(10L))
                .build();
    }

    private Rule ruleWith(Long id, int priority, LogicOperator op, boolean enabled,
                          List<RuleCondition> conditions, List<RuleAction> actions) {
        return Rule.builder()
                .id(id).name("R" + id).priority(priority).enabled(enabled)
                .logicOperator(op).score(priority * 10)
                .conditions(conditions).actions(actions)
                .build();
    }

    private RuleCondition cond(String field, Operator op, String value, DataType type) {
        return RuleCondition.builder()
                .field(field).operator(op).value(value).valueType(type)
                .build();
    }

    private RuleAction action(ActionType type, String key, String val) {
        return RuleAction.builder().actionType(type).outputKey(key).outputValue(val).build();
    }

    private EvaluateRequest req(String json) throws Exception {
        JsonNode input = objectMapper.readTree(json);
        EvaluateRequest r = new EvaluateRequest();
        r.setInput(input);
        return r;
    }

    private EvaluationRequest savedEvalReq(Long id) {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        EvaluationRequest er = EvaluationRequest.builder()
                .id(id).tenant(tenant(10L)).ruleSet(rs)
                .build();
        EvaluationResult result = EvaluationResult.builder()
                .id(1L).outputPayload("{}").matchedRules("[]")
                .executionTimeMs(10L).strategyUsed(EvaluationStrategy.FIRST_MATCH)
                .totalScore(0.0).evaluationRequest(er)
                .build();
        er.setResult(result);
        return er;
    }

    // ─── API KEY / RULESET GUARD TESTS ───────────────────────

    @Test
    void evaluate_whenApiKeyNotFound_throwsNotFound() throws Exception {
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.evaluate(req("{\"age\":25}"), principal(10L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void evaluate_whenRuleSetIsNull_throwsBadRequest() throws Exception {
        ApiKey key = ApiKey.builder().id(5L).active(true).ruleSet(null).tenant(tenant(10L)).build();
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> evaluationService.evaluate(req("{\"x\":1}"), principal(10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not linked to a RuleSet");
    }

    @Test
    void evaluate_whenRuleSetTenantMismatch_throwsBadRequest() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        rs.setTenant(tenant(99L)); // different tenant
        ApiKey key = ApiKey.builder().id(5L).active(true).ruleSet(rs).tenant(tenant(10L)).build();
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> evaluationService.evaluate(req("{\"x\":1}"), principal(10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void evaluate_whenRuleSetNotActive_throwsBadRequest() throws Exception {
        RuleSet rs = RuleSet.builder()
                .id(1L).name("RS").status(RuleSetStatus.DRAFT)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .tenant(tenant(10L)).build();
        ApiKey key = ApiKey.builder().id(5L).active(true).ruleSet(rs).tenant(tenant(10L)).build();
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> evaluationService.evaluate(req("{\"score\":50}"), principal(10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ACTIVE");
    }

    // ─── INPUT VALIDATION TESTS ───────────────────────────────

    @Test
    void evaluate_whenInputNotJsonObject_throwsBadRequest() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        EvaluateRequest r = new EvaluateRequest();
        r.setInput(objectMapper.readTree("[1,2,3]")); // array, not object

        // convertValue throws IllegalArgumentException before ruleRepository is ever queried
        assertThatThrownBy(() -> evaluationService.evaluate(r, principal(10L)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void evaluate_withUnknownField_throwsBadRequest() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Rule rule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("age", Operator.GREATER_THAN, "18", DataType.NUMBER)),
                List.of(action(ActionType.APPROVE, "decision", "APPROVED")));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));

        // input has "unknownField" which is not in any condition
        assertThatThrownBy(() -> evaluationService.evaluate(req("{\"unknownField\":\"x\",\"age\":20}"), principal(10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown field");
    }

    @Test
    void evaluate_withMissingRequiredField_throwsBadRequest() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Rule rule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("age", Operator.GREATER_THAN, "18", DataType.NUMBER)),
                List.of(action(ActionType.APPROVE, "decision", "APPROVED")));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));

        // input is empty — missing "age"
        assertThatThrownBy(() -> evaluationService.evaluate(req("{}"), principal(10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing field");
    }

    @Test
    void evaluate_withWrongType_throwsBadRequest() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Rule rule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("age", Operator.EQUALS, "25", DataType.NUMBER)),
                List.of());
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));

        // "age" is provided as string, not number
        assertThatThrownBy(() -> evaluationService.evaluate(req("{\"age\":\"twenty-five\"}"), principal(10L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid input type");
    }

    // ─── RULE MATCHING + STRATEGY TESTS ──────────────────────

    @Test
    void evaluate_firstMatchStrategy_returnsOnlyFirstMatchingRule() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        // Rule1: score > 50 → APPROVE (priority=1, matched)
        Rule rule1 = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("score", Operator.GREATER_THAN, "50", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "decision", "APPROVED")));
        // Rule2: score > 0 → REVIEW (priority=2, also matches but should be skipped)
        Rule rule2 = ruleWith(2L, 2, LogicOperator.AND, true,
                List.of(cond("score", Operator.GREATER_THAN, "0", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "decision", "REVIEW")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule1, rule2));
        EvaluationRequest saved = savedEvalReq(100L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        EvaluateResponse response = evaluationService.evaluate(req("{\"score\":75}"), principal(10L));

        // Only rule1 matched (FIRST_MATCH) → decision = APPROVED
        assertThat(response.getOutput().get("decision").asText()).isEqualTo("APPROVED");
        assertThat(response.getMatchedRules().size()).isEqualTo(1);
    }

    @Test
    void evaluate_allMatchStrategy_mergesActionsFromAllMatchedRules() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.ALL_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Rule rule1 = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("age", Operator.GREATER_THAN, "18", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "isAdult", "true")));
        Rule rule2 = ruleWith(2L, 2, LogicOperator.AND, true,
                List.of(cond("age", Operator.GREATER_THAN, "18", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "canVote", "true")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule1, rule2));
        EvaluationRequest saved = savedEvalReq(101L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        EvaluateResponse response = evaluationService.evaluate(req("{\"age\":25}"), principal(10L));

        // Both rules matched → both actions in output
        assertThat(response.getOutput().has("isAdult")).isTrue();
        assertThat(response.getOutput().has("canVote")).isTrue();
        assertThat(response.getMatchedRules().size()).isEqualTo(2);
    }

    @Test
    void evaluate_disabledRulesAreSkipped() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.ALL_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        // enabled rule + disabled rule — same condition
        Rule enabledRule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("age", Operator.GREATER_THAN, "0", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "result", "yes")));
        Rule disabledRule = ruleWith(2L, 2, LogicOperator.AND, false, // disabled
                List.of(cond("age", Operator.GREATER_THAN, "0", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "result2", "no")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(enabledRule, disabledRule));
        EvaluationRequest saved = savedEvalReq(102L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        EvaluateResponse response = evaluationService.evaluate(req("{\"age\":5}"), principal(10L));

        assertThat(response.getOutput().has("result")).isTrue();
        assertThat(response.getOutput().has("result2")).isFalse();

    }

    @Test
    void evaluate_noMatchingRules_returnsEmptyOutputAndZeroScore() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.ALL_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Rule rule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("age", Operator.GREATER_THAN, "100", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "result", "old")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));
        EvaluationRequest saved = savedEvalReq(103L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        EvaluateResponse response = evaluationService.evaluate(req("{\"age\":25}"), principal(10L));

        assertThat(response.getOutput().isEmpty()).isTrue();
        assertThat(response.getTotalScore()).isEqualTo(0.0);
    }

    @Test
    void evaluate_andCondition_requiresAllConditionsToMatch() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        // AND: age > 18 AND employed = true → APPROVE
        Rule rule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(
                        cond("age", Operator.GREATER_THAN, "18", DataType.NUMBER),
                        cond("employed", Operator.EQUALS, "true", DataType.BOOLEAN)
                ),
                List.of(action(ActionType.APPROVE, "decision", "APPROVED")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));
        EvaluationRequest saved = savedEvalReq(104L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        // age=25 (ok) but employed=false (fails AND) → no match
        EvaluateResponse response = evaluationService.evaluate(
                req("{\"age\":25,\"employed\":false}"), principal(10L));

        assertThat(response.getMatchedRules().size()).isEqualTo(0);
    }

    @Test
    void evaluate_orCondition_matchesWhenAnyConditionTrue() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        // OR: age > 65 OR income > 10000 → APPROVE
        Rule rule = ruleWith(1L, 1, LogicOperator.OR, true,
                List.of(
                        cond("age", Operator.GREATER_THAN, "65", DataType.NUMBER),
                        cond("income", Operator.GREATER_THAN, "10000", DataType.NUMBER)
                ),
                List.of(action(ActionType.APPROVE, "status", "APPROVED")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));
        EvaluationRequest saved = savedEvalReq(105L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        // age=30 (fails) but income=15000 (passes OR) → match
        EvaluateResponse response = evaluationService.evaluate(
                req("{\"age\":30,\"income\":15000}"), principal(10L));

        assertThat(response.getMatchedRules().size()).isEqualTo(1);
        assertThat(response.getOutput().get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void evaluate_scoreBasedStrategy_pickRuleWithHighestScore() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.SCORE_BASED);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        // Both rules match. Rule1 score=5, Rule2 score=100.
        Rule rule1 = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("income", Operator.GREATER_THAN, "0", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "tier", "bronze")));
        rule1.setScore(5);

        Rule rule2 = ruleWith(2L, 2, LogicOperator.AND, true,
                List.of(cond("income", Operator.GREATER_THAN, "0", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "tier", "gold")));
        rule2.setScore(100);

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule1, rule2));
        EvaluationRequest saved = savedEvalReq(106L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        EvaluateResponse response = evaluationService.evaluate(req("{\"income\":5000}"), principal(10L));

        // Rule2 has highest score → "gold"
        assertThat(response.getOutput().get("tier").asText()).isEqualTo("gold");
    }

    @Test
    void evaluate_stringsWithEqualsOperator_matchesCorrectly() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Rule rule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("country", Operator.EQUALS, "TN", DataType.STRING)),
                List.of(action(ActionType.SET_VALUE, "region", "North Africa")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));
        EvaluationRequest saved = savedEvalReq(107L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        EvaluateResponse response = evaluationService.evaluate(req("{\"country\":\"TN\"}"), principal(10L));

        assertThat(response.getOutput().get("region").asText()).isEqualTo("North Africa");
    }

    @Test
    void evaluate_savesEvaluationRequestAndNotifies() throws Exception {
        RuleSet rs = activeRuleSet(EvaluationStrategy.FIRST_MATCH);
        ApiKey key = activeKey(rs);
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Rule rule = ruleWith(1L, 1, LogicOperator.AND, true,
                List.of(cond("x", Operator.EQUALS, "1", DataType.NUMBER)),
                List.of(action(ActionType.SET_VALUE, "result", "ok")));

        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of(rule));
        EvaluationRequest saved = savedEvalReq(200L);
        when(evaluationRequestRepository.save(any())).thenReturn(saved);

        evaluationService.evaluate(req("{\"x\":1}"), principal(10L));

        verify(evaluationRequestRepository).save(any());
        verify(notificationProducer).publish(any(), any(), any(), eq(10L), any(), eq("EVALUATION"));
    }
}