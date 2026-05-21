package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.SimulationResult;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.RuleSimulationServiceImpl;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SimulationController.class)
@AutoConfigureMockMvc(addFilters = false)
class SimulationControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RuleSimulationServiceImpl simulationService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("10");
    }

    private SimulationResult simulationResult() {
        return SimulationResult.builder()
                .totalEvaluated(50).changedCount(10)
                .changePercentage(20.0).improvedCount(8).worsenedCount(2)
                .avgScoreBefore(75.0).avgScoreAfter(78.0).avgScoreDelta(3.0)
                .sampleDiffs(List.of())
                .aiAnalysis("Les résultats montrent une amélioration.")
                .aiAvailable(true)
                .build();
    }

    // ─── SIMULATE ────────────────────────────────────────────

    @Test
    void simulate_returns200WithResult() throws Exception {
        stubTenant();
        when(simulationService.simulate(eq(5L), eq(10L), any())).thenReturn(simulationResult());

        mockMvc.perform(post("/api/ruleset/5/simulate")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ruleId", 2, "sampleSize", 50,
                                "newScore", 85, "newLogicOperator", "AND"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluated").value(50))
                .andExpect(jsonPath("$.changedCount").value(10))
                .andExpect(jsonPath("$.changePercentage").value(20.0))
                .andExpect(jsonPath("$.aiAvailable").value(true));
    }

    @Test
    void simulate_returns404WhenRuleSetNotFound() throws Exception {
        stubTenant();
        when(simulationService.simulate(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleSet not found with id: 5"));

        mockMvc.perform(post("/api/ruleset/5/simulate")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sampleSize", 50))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("RuleSet not found with id: 5"));
    }

    @Test
    void simulate_returnsResultWithoutAiWhenUnavailable() throws Exception {
        stubTenant();
        SimulationResult noAi = SimulationResult.builder()
                .totalEvaluated(30).changedCount(0)
                .changePercentage(0.0).avgScoreBefore(70.0).avgScoreAfter(70.0)
                .avgScoreDelta(0.0).sampleDiffs(List.of())
                .aiAnalysis(null).aiAvailable(false)
                .build();

        when(simulationService.simulate(any(), any(), any())).thenReturn(noAi);

        mockMvc.perform(post("/api/ruleset/5/simulate")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sampleSize", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAvailable").value(false))
                .andExpect(jsonPath("$.totalEvaluated").value(30));
    }
}