package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleSetDocumentationAgentTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock AiInsightRepository aiInsightRepository;
    @Mock LlmClient llmClient;

    RuleSetDocumentationAgent agent;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        agent = new RuleSetDocumentationAgent(ruleSetRepository, aiInsightRepository, llmClient, new ObjectMapper());
    }

    private Tenant tenant(Long id) {
        return Tenant.builder().id(id).name("Acme").build();
    }

    private RuleSet ruleSet(Long id) {
        Tenant t = tenant(10L);
        RuleCondition cond = new RuleCondition();
        cond.setField("age");
        cond.setOperator(Operator.GREATER_THAN);
        cond.setValue("18");
        cond.setValueType(DataType.NUMBER);

        RuleAction action = new RuleAction();
        action.setActionType(ActionType.APPROVE);
        action.setOutputKey("decision");
        action.setOutputValue("APPROVED");

        Rule rule = Rule.builder()
                .id(1L).name("Age Check").priority(1).enabled(true)
                .conditions(List.of(cond))
                .actions(List.of(action))
                .build();

        return RuleSet.builder()
                .id(id).name("CreditRS").tenant(t)
                .currentVersion(2)
                .evaluationStrategy(talan.pfe.rulengine.enums.EvaluationStrategy.FIRST_MATCH)
                .rules(List.of(rule))
                .build();
    }

    @Test
    void generateDocumentation_whenRuleSetNotFound_returnsWithoutLlmCall() {
        when(ruleSetRepository.findByIdAndTenantIdWithRules(99L, 10L)).thenReturn(Optional.empty());

        agent.generateDocumentation(99L, 10L);

        verifyNoInteractions(llmClient, aiInsightRepository);
    }

    @Test
    void generateDocumentation_callsLlmAndSavesInsight() {
        RuleSet rs = ruleSet(1L);
        when(ruleSetRepository.findByIdAndTenantIdWithRules(1L, 10L)).thenReturn(Optional.of(rs));
        when(llmClient.generate(any(), any())).thenReturn("Detailed documentation content.");
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agent.generateDocumentation(1L, 10L);

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());
        AiInsight saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(InsightType.DOCUMENTATION_GENERATED);
        assertThat(saved.getDescription()).isEqualTo("Detailed documentation content.");
        assertThat(saved.getConfidence()).isEqualTo(0.87f);
        assertThat(saved.getTitle()).contains("CreditRS");
        assertThat(saved.getRuleSet()).isEqualTo(rs);
    }

    @Test
    void generateDocumentation_whenLlmFails_savesFallbackContent() {
        RuleSet rs = ruleSet(1L);
        when(ruleSetRepository.findByIdAndTenantIdWithRules(1L, 10L)).thenReturn(Optional.of(rs));
        when(llmClient.generate(any(), any())).thenThrow(new RuntimeException("LLM unavailable"));
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agent.generateDocumentation(1L, 10L);

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).contains("CreditRS");
    }

    @Test
    void generateDocumentation_insightHasContextDataWithRuleCount() {
        RuleSet rs = ruleSet(1L);
        when(ruleSetRepository.findByIdAndTenantIdWithRules(1L, 10L)).thenReturn(Optional.of(rs));
        when(llmClient.generate(any(), any())).thenReturn("Generated doc.");
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agent.generateDocumentation(1L, 10L);

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());
        String ctx = captor.getValue().getContextData();
        assertThat(ctx).contains("ruleCount");
        assertThat(ctx).contains("version");
    }
}