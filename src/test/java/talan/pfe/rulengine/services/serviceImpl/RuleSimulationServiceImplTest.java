package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import talan.pfe.rulengine.dtos.request.SimulationRequest;
import talan.pfe.rulengine.dtos.response.SimulationResult;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.RuleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleSimulationServiceImpl")
class RuleSimulationServiceImplTest {

    @Mock EvaluationRequestRepository evaluationRequestRepository;
    @Mock RuleRepository ruleRepository;
    @Mock ChatClient chatClient;

    @InjectMocks RuleSimulationServiceImpl service;

    private Rule originalRule;
    private EvaluationRequest evalWithAmount500;
    private EvaluationRequest evalWithAmount200;

    @BeforeEach
    void setUp() {
        RuleCondition condition = new RuleCondition();
        condition.setField("amount");
        condition.setOperator(Operator.GREATER_THAN);
        condition.setValue("300");
        condition.setValueType(DataType.NUMBER);

        originalRule = new Rule();
        originalRule.setId(1L);
        originalRule.setName("High Amount Rule");
        originalRule.setScore(10);
        originalRule.setLogicOperator(LogicOperator.AND);
        originalRule.setConditions(List.of(condition));
        originalRule.setActions(new ArrayList<>());

        Tenant tenant = Tenant.builder().id(1L).name("T").build();

        evalWithAmount500 = EvaluationRequest.builder()
                .id(1L).tenant(tenant)
                .inputPayload("{\"amount\":500}")
                .requestedAt(LocalDateTime.now()).build();

        evalWithAmount200 = EvaluationRequest.builder()
                .id(2L).tenant(tenant)
                .inputPayload("{\"amount\":200}")
                .requestedAt(LocalDateTime.now()).build();
    }

    @Nested
    @DisplayName("simulate() with no history")
    class NoHistory {

        @Test
        @DisplayName("should return zero-result response when no past evaluations exist")
        void simulate_noHistory_returnsZeroResult() {
            Page<EvaluationRequest> emptyPage = new PageImpl<>(List.of());
            when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(1L), any()))
                    .thenReturn(emptyPage);

            SimulationRequest req = buildSimulationRequest();
            SimulationResult result = service.simulate(10L, 1L, req);

            assertThat(result.getTotalEvaluated()).isZero();
            assertThat(result.isAiAvailable()).isFalse();
            assertThat(result.getAiAnalysis()).contains("Aucune évaluation");
        }
    }

    @Nested
    @DisplayName("simulate() score calculation")
    class ScoreCalculation {

        @Test
        @DisplayName("should detect change when proposed conditions match more evaluations")
        void simulate_proposedConditionsMatchMore_detectsChange() {
            Page<EvaluationRequest> historyPage = new PageImpl<>(
                    List.of(evalWithAmount500, evalWithAmount200));
            when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(1L), any()))
                    .thenReturn(historyPage);
            when(ruleRepository.findById(1L)).thenReturn(Optional.of(originalRule));

            SimulationRequest req = buildSimulationRequest();
            req.getProposedConditions().get(0).setValue("100");

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content()).thenReturn("Analyse IA: modification recommandée.");

            SimulationResult result = service.simulate(10L, 1L, req);

            assertThat(result.getTotalEvaluated()).isEqualTo(2);
            assertThat(result.getChangedCount()).isGreaterThanOrEqualTo(1);
            assertThat(result.isAiAvailable()).isTrue();
            assertThat(result.getAiAnalysis()).isNotBlank();
        }

        @Test
        @DisplayName("should report no changes when same conditions produce identical results")
        void simulate_sameConditions_noChange() {
            Page<EvaluationRequest> historyPage = new PageImpl<>(List.of(evalWithAmount500));
            when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(1L), any()))
                    .thenReturn(historyPage);
            when(ruleRepository.findById(1L)).thenReturn(Optional.of(originalRule));

            SimulationRequest req = buildSimulationRequest();

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callSpec);
            when(callSpec.content()).thenReturn("Aucun changement détecté.");

            SimulationResult result = service.simulate(10L, 1L, req);

            assertThat(result.getTotalEvaluated()).isEqualTo(1);
            assertThat(result.getChangedCount()).isZero();
        }
    }

    @Nested
    @DisplayName("simulate() when AI is unavailable")
    class AiUnavailable {

        @Test
        @DisplayName("should return aiAvailable=false and fallback message when Groq throws")
        void simulate_groqThrows_aiUnavailable() {
            Page<EvaluationRequest> historyPage = new PageImpl<>(List.of(evalWithAmount500));
            when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(1L), any()))
                    .thenReturn(historyPage);
            when(ruleRepository.findById(1L)).thenReturn(Optional.of(originalRule));

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);
            when(requestSpec.call()).thenThrow(new RuntimeException("Groq timeout"));

            SimulationResult result = service.simulate(10L, 1L, buildSimulationRequest());

            assertThat(result.isAiAvailable()).isFalse();
            assertThat(result.getAiAnalysis()).isEqualTo("Analyse IA indisponible.");
        }
    }

    @Test
    @DisplayName("should throw RuntimeException when rule not found")
    void simulate_ruleNotFound_throwsRuntime() {
        Page<EvaluationRequest> historyPage = new PageImpl<>(List.of(evalWithAmount500));
        when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(eq(1L), any()))
                .thenReturn(historyPage);
        when(ruleRepository.findById(999L)).thenReturn(Optional.empty());

        SimulationRequest req = buildSimulationRequest();
        req.setRuleId(999L);

        assertThatThrownBy(() -> service.simulate(10L, 1L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Rule not found");
    }

    @Test
    @DisplayName("should default sampleSize to 100 when 0 is provided")
    void simulate_zeroSampleSize_defaults100() {
        Page<EvaluationRequest> emptyPage = new PageImpl<>(List.of());
        when(evaluationRequestRepository.findByTenantIdOrderByRequestedAtDesc(1L,
                PageRequest.of(0, 100))).thenReturn(emptyPage);

        SimulationRequest req = buildSimulationRequest();
        req.setSampleSize(0);

        SimulationResult result = service.simulate(10L, 1L, req);
        assertThat(result.getTotalEvaluated()).isZero();
        verify(evaluationRequestRepository).findByTenantIdOrderByRequestedAtDesc(
                1L, PageRequest.of(0, 100));
    }

    private SimulationRequest buildSimulationRequest() {
        SimulationRequest.ProposedCondition pc = new SimulationRequest.ProposedCondition();
        pc.setField("amount");
        pc.setOperator("GREATER_THAN");
        pc.setValue("300");
        pc.setValueType("NUMBER");

        SimulationRequest req = new SimulationRequest();
        req.setRuleId(1L);
        req.setSampleSize(100);
        req.setProposedConditions(List.of(pc));
        return req;
    }
}