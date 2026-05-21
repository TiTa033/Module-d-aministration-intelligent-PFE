package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.ExternalDataCollectionAgent;
import talan.pfe.rulengine.services.serviceImpl.N8nWebhookService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ExternalDataController.class)
@AutoConfigureMockMvc(addFilters = false)
// Force n8n delegation OFF so trigger goes to the Java agent (not n8n)
@TestPropertySource(properties = "n8n.collect.webhook-url=")
class ExternalDataControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ExternalDataCollectionAgent externalDataCollectionAgent;
    @MockBean AiInsightRepository         aiInsightRepository;
    @MockBean JwtService                  jwtService;
    @MockBean JwtAuthenticationFilter     jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter  apiKeyAuthenticationFilter;
    @MockBean N8nWebhookService           n8nWebhookService;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("1");
    }

    private talan.pfe.rulengine.entites.AiInsight insightEntity(Long id, String title) {
        return talan.pfe.rulengine.entites.AiInsight.builder()
                .id(id)
                .type(InsightType.EXTERNAL_RECOMMENDATION)
                .agentType(AgentType.EXTERNAL_ANALYSIS_AGENT)
                .title(title)
                .description("Analyse financière externe du mois.")
                .confidence(0.82f)
                .status(InsightStatus.PENDING)
                .tenant(talan.pfe.rulengine.entites.Tenant.builder().id(1L).name("T").slug("t").build())
                .build();
    }

    // ─── TRIGGER ─────────────────────────────────────────────

    @Test
    void trigger_returns202Accepted() throws Exception {
        doNothing().when(externalDataCollectionAgent).collectAndAnalyze();

        mockMvc.perform(post("/api/external-analysis/trigger"))
                .andExpect(status().isAccepted());

        verify(externalDataCollectionAgent).collectAndAnalyze();
    }

    @Test
    void trigger_callsAgentExactlyOnce() throws Exception {
        doNothing().when(externalDataCollectionAgent).collectAndAnalyze();

        mockMvc.perform(post("/api/external-analysis/trigger"))
                .andExpect(status().isAccepted());

        verify(externalDataCollectionAgent, times(1)).collectAndAnalyze();
        verifyNoInteractions(n8nWebhookService);
    }

    // ─── LIST INSIGHTS ────────────────────────────────────────

    @Test
    void listInsights_withDefaultPendingStatus_returns200WithList() throws Exception {
        stubTenant();
        var entity = insightEntity(1L, "Analyse Externe – MAY 2026 [Banque Centrale · Macro-éco · Sectoriel]");
        when(aiInsightRepository.findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(
                eq(1L), eq(InsightType.EXTERNAL_RECOMMENDATION), eq(InsightStatus.PENDING)))
                .thenReturn(List.of(entity));

        mockMvc.perform(get("/api/external-analysis/insights")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value(
                        "Analyse Externe – MAY 2026 [Banque Centrale · Macro-éco · Sectoriel]"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void listInsights_withAcceptedStatus_filtersCorrectly() throws Exception {
        stubTenant();
        when(aiInsightRepository.findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(
                eq(1L), eq(InsightType.EXTERNAL_RECOMMENDATION), eq(InsightStatus.ACCEPTED)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/external-analysis/insights")
                        .param("status", "ACCEPTED")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listInsights_withUnknownStatus_defaultsToPending() throws Exception {
        stubTenant();
        when(aiInsightRepository.findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(
                eq(1L), eq(InsightType.EXTERNAL_RECOMMENDATION), eq(InsightStatus.PENDING)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/external-analysis/insights")
                        .param("status", "INVALID_STATUS")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());

        verify(aiInsightRepository).findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(
                eq(1L), eq(InsightType.EXTERNAL_RECOMMENDATION), eq(InsightStatus.PENDING));
    }

    @Test
    void listInsights_returnsEmptyListWhenNoInsights() throws Exception {
        stubTenant();
        when(aiInsightRepository.findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/external-analysis/insights")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listInsights_multipleInsights_returnsAll() throws Exception {
        stubTenant();
        when(aiInsightRepository.findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(any(), any(), any()))
                .thenReturn(List.of(
                        insightEntity(1L, "Analyse Externe – MAY 2026"),
                        insightEntity(2L, "Analyse Externe – APR 2026")));

        mockMvc.perform(get("/api/external-analysis/insights")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }
}
