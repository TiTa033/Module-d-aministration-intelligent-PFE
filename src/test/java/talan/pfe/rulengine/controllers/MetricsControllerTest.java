package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.RuleSetMetricsResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.MetricsService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MetricsController")
class MetricsControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean MetricsService metricsService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private RuleSetMetricsResponse stubMetrics(int period) {
        return RuleSetMetricsResponse.builder()
                .ruleSetId(10L)
                .ruleSetName("Credit RS")
                .periodDays(period)
                .totalEvaluations(200L)
                .avgExecutionMs(85.0)
                .dailyStats(List.of())
                .build();
    }

    @Test @DisplayName("GET /api/metrics/rulesets/{id} → 200 OK (default period=30)")
    void getRuleSetMetrics_defaultPeriod_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(metricsService.getRuleSetMetrics(10L, 1L, 30)).thenReturn(stubMetrics(30));

        mockMvc.perform(get("/api/metrics/rulesets/10")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/metrics/rulesets/{id}?period=7 → 200 OK")
    void getRuleSetMetrics_customPeriod_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(metricsService.getRuleSetMetrics(10L, 1L, 7)).thenReturn(stubMetrics(7));

        mockMvc.perform(get("/api/metrics/rulesets/10")
                        .param("period", "7")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/metrics/rulesets/{id} → 200 OK with null tenantId")
    void getRuleSetMetrics_nullTenantId_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn(null);
        when(metricsService.getRuleSetMetrics(10L, null, 30)).thenReturn(stubMetrics(30));

        mockMvc.perform(get("/api/metrics/rulesets/10")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }
}
