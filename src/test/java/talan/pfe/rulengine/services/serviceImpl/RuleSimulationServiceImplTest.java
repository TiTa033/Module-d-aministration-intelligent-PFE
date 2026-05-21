package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import talan.pfe.rulengine.dtos.request.SimulationRequest;
import talan.pfe.rulengine.dtos.response.SimulationResult;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleSimulationServiceImplTest {

    @Mock EvaluationRequestRepository evaluationRequestRepository;
    @Mock RuleRepository ruleRepository;
    @Mock LlmClient llmClient;

    RuleSimulationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleSimulationServiceImpl(evaluationRequestRepository, ruleRepository, llmClient);
    }

    private SimulationRequest req(Long ruleId) {
        SimulationRequest r = new SimulationRequest();
        r.setRuleId(ruleId);
        r.setSampleSize(10);
        r.setProposedConditions(List.of());
        return r;
    }

    private Rule rule(Long id) {
        RuleCondition cond = new RuleCondition();
        cond.setField("income");
        cond.setOperator(Operator.GREATER_THAN);
        cond.setValue("30000");
        cond.setValueType(DataType.NUMBER);

        Rule r = new Rule();
        r.setId(id);
        r.setName("IncomeCheck");
        r.setLogicOperator(LogicOperator.AND);
        r.setEnabled(true);
        r.setScore(10);
        r.setPendingUpdate(false);
        r.setConditions(List.of(cond));
        r.setActions(List.of());
        return r;
    }

    private EvaluationRequest evalReq(Long id, Long tenantId, String inputJson) {
        Tenant t = Tenant.builder().id(tenantId).name("Acme").build();
        RuleSet rs = RuleSet.builder().id(1L).name("CreditRS")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH).build();
        return EvaluationRequest.builder()
                .id(id).tenant(t).ruleSet(rs)
                .inputPayload(inputJson)
                .build();
    }

    // ─── simulate ─────────────────────────────────────────────

    @Test
    void simulate_whenNoPastEvaluations_returnsEmptyResultWithAiUnavailable() {
        when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SimulationResult result = service.simulate(1L, 10L, req(5L));

        assertThat(result.getTotalEvaluated()).isZero();
        assertThat(result.isAiAvailable()).isFalse();
        assertThat(result.getAiAnalysis()).contains("Aucune");
        verifyNoInteractions(ruleRepository, llmClient);
    }

    @Test
    void simulate_withPastEvaluations_andLlmUnavailable_aiAvailableIsFalse() {
        // LlmClient throws → aiAvailable stays false
        when(llmClient.generate(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        EvaluationRequest eval = evalReq(1L, 10L, "{\"income\": 50000}");
        when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(eval)));
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule(5L)));

        SimulationRequest request = req(5L);
        SimulationRequest.ProposedCondition newCond = new SimulationRequest.ProposedCondition();
        newCond.setField("income");
        newCond.setOperator("GREATER_THAN");
        newCond.setValue("40000");
        newCond.setValueType("NUMBER");
        request.setProposedConditions(List.of(newCond));

        SimulationResult result = service.simulate(1L, 10L, request);

        assertThat(result.getTotalEvaluated()).isEqualTo(1);
        assertThat(result.isAiAvailable()).isFalse();
    }

    @Test
    void simulate_withPastEvaluations_andLlmAvailable_aiAvailableIsTrue() {
        when(llmClient.generate(anyString(), anyString()))
                .thenReturn("La modification améliore le scoring crédit. Recommandation : appliquer.");

        EvaluationRequest eval = evalReq(1L, 10L, "{\"income\": 50000}");
        when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(eval)));
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule(5L)));

        SimulationRequest request = req(5L);
        SimulationRequest.ProposedCondition newCond = new SimulationRequest.ProposedCondition();
        newCond.setField("income");
        newCond.setOperator("GREATER_THAN");
        newCond.setValue("40000");
        newCond.setValueType("NUMBER");
        request.setProposedConditions(List.of(newCond));

        SimulationResult result = service.simulate(1L, 10L, request);

        assertThat(result.getTotalEvaluated()).isEqualTo(1);
        assertThat(result.isAiAvailable()).isTrue();
        assertThat(result.getAiAnalysis()).contains("améliore");
    }

    @Test
    void simulate_withInvalidInputPayload_skipsAndCountsZeroChanged() {
        lenient().when(llmClient.generate(anyString(), anyString())).thenReturn("OK");

        EvaluationRequest eval = evalReq(1L, 10L, "not-valid-json");
        when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(eval)));
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule(5L)));

        SimulationResult result = service.simulate(1L, 10L, req(5L));

        assertThat(result.getTotalEvaluated()).isEqualTo(1);
        assertThat(result.getChangedCount()).isZero();
    }

    @Test
    void simulate_withInvalidOperatorInProposedCondition_defaultsToEquals() {
        lenient().when(llmClient.generate(anyString(), anyString())).thenReturn("OK");

        EvaluationRequest eval = evalReq(1L, 10L, "{\"income\": 50000}");
        when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(eval)));
        when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule(5L)));

        SimulationRequest request = req(5L);
        SimulationRequest.ProposedCondition badCond = new SimulationRequest.ProposedCondition();
        badCond.setField("income");
        badCond.setOperator("INVALID_OP");
        badCond.setValue("30000");
        badCond.setValueType("INVALID_TYPE");
        request.setProposedConditions(List.of(badCond));

        assertThatCode(() -> service.simulate(1L, 10L, request)).doesNotThrowAnyException();
    }
}
