package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.llm.LlmClient;
import talan.pfe.rulengine.services.llm.TavilyWebSearchClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalDataCollectionAgentTest {

    @Mock TavilyWebSearchClient tavilyClient;
    @Mock LlmClient llmClient;
    @Mock AiInsightRepository aiInsightRepository;
    @Mock TenantRepository tenantRepository;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks ExternalDataCollectionAgent agent;

    // ─── helpers ─────────────────────────────────────────────

    private Tenant activeTenant(Long id) {
        return Tenant.builder().id(id).name("T" + id).slug("t" + id).status(TenantStatus.ACTIVE).build();
    }

    private Tenant inactiveTenant(Long id) {
        return Tenant.builder().id(id).name("I" + id).slug("i" + id).status(TenantStatus.INACTIVE).build();
    }

    private TavilyWebSearchClient.SearchResponse tavilyResp(String answer) {
        TavilyWebSearchClient.SearchResponse resp = new TavilyWebSearchClient.SearchResponse();
        resp.setAnswer(answer);
        resp.setResults(List.of());
        return resp;
    }

    // ─── collectAndAnalyze ───────────────────────────────────

    @Test
    void collectAndAnalyze_whenAllSearchesReturnData_savesInsightForEachActiveTenant() {
        when(tavilyClient.search(any(), anyInt())).thenReturn(tavilyResp("Some financial data."));
        when(llmClient.generate(any(), any())).thenReturn("## Analyse\nRésumé financier du mois.");
        when(tenantRepository.findAll()).thenReturn(List.of(activeTenant(1L), activeTenant(2L)));
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agent.collectAndAnalyze();

        // 3 Tavily searches (Banque Centrale, Macro, Sectoriel) expected
        verify(tavilyClient, times(3)).search(any(), eq(5));
        // One insight saved per active tenant
        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository, times(2)).save(captor.capture());

        AiInsight captured = captor.getAllValues().get(0);
        assertThat(captured.getType()).isEqualTo(InsightType.EXTERNAL_RECOMMENDATION);
        assertThat(captured.getAgentType()).isEqualTo(AgentType.EXTERNAL_ANALYSIS_AGENT);
        assertThat(captured.getConfidence()).isEqualTo(0.82f);
        assertThat(captured.getTitle()).contains("Banque Centrale");
    }

    @Test
    void collectAndAnalyze_whenNoData_abortsWithoutSavingInsights() {
        // Tavily returns null for all searches — agent aborts before iterating tenants
        when(tavilyClient.search(any(), anyInt())).thenReturn(null);

        agent.collectAndAnalyze();

        // Nothing should be saved when all searches return null
        verify(aiInsightRepository, never()).save(any());
    }

    @Test
    void collectAndAnalyze_skipsInactiveTenants() {
        when(tavilyClient.search(any(), anyInt())).thenReturn(tavilyResp("Data."));
        when(llmClient.generate(any(), any())).thenReturn("Analysis result.");
        // One active, one inactive tenant
        when(tenantRepository.findAll()).thenReturn(List.of(activeTenant(1L), inactiveTenant(2L)));
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agent.collectAndAnalyze();

        // Only 1 insight saved (for active tenant only)
        verify(aiInsightRepository, times(1)).save(any());
    }

    @Test
    void collectAndAnalyze_whenLlmFails_usesFallbackDescription() {
        when(tavilyClient.search(any(), anyInt())).thenReturn(tavilyResp("Market data here."));
        when(llmClient.generate(any(), any())).thenThrow(new RuntimeException("LLM unavailable"));
        when(tenantRepository.findAll()).thenReturn(List.of(activeTenant(1L)));
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw — fallback kicks in
        assertThatCode(agent::collectAndAnalyze).doesNotThrowAnyException();

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());
        // Fallback report header
        assertThat(captor.getValue().getDescription()).contains("Rapport Externe");
    }

    @Test
    void collectAndAnalyze_insightDescriptionIsTruncatedTo2000Chars() {
        // LLM returns a very long response
        String longResponse = "A".repeat(5000);
        when(tavilyClient.search(any(), anyInt())).thenReturn(tavilyResp("Data."));
        when(llmClient.generate(any(), any())).thenReturn(longResponse);
        when(tenantRepository.findAll()).thenReturn(List.of(activeTenant(1L)));
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agent.collectAndAnalyze();

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription().length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void collectAndAnalyze_insightContextDataContainsPeriodAndSources() {
        when(tavilyClient.search(any(), anyInt())).thenReturn(tavilyResp("Inflation data."));
        when(llmClient.generate(any(), any())).thenReturn("Analysis.");
        when(tenantRepository.findAll()).thenReturn(List.of(activeTenant(1L)));
        when(aiInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agent.collectAndAnalyze();

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());
        String context = captor.getValue().getContextData();
        assertThat(context).contains("period");
        assertThat(context).contains("Tavily");
    }

    @Test
    void collectAndAnalyze_whenTavilyThrowsException_doesNotCrash() {
        when(tavilyClient.search(any(), anyInt())).thenThrow(new RuntimeException("Network error"));

        // Even if Tavily crashes, the agent should not propagate the exception (empty data → abort)
        assertThatCode(agent::collectAndAnalyze).doesNotThrowAnyException();
        verify(aiInsightRepository, never()).save(any());
    }

    @Test
    void collectAndAnalyze_whenNoActiveTenants_noInsightsSaved() {
        when(tavilyClient.search(any(), anyInt())).thenReturn(tavilyResp("Data."));
        when(llmClient.generate(any(), any())).thenReturn("Analysis.");
        when(tenantRepository.findAll()).thenReturn(List.of(inactiveTenant(1L), inactiveTenant(2L)));

        agent.collectAndAnalyze();

        verify(aiInsightRepository, never()).save(any());
    }
}