package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.SimulationResult;
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
@DisplayName("SimulationController")
class SimulationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean RuleSimulationServiceImpl simulationService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test @DisplayName("POST /api/ruleset/{id}/simulate → 200 OK")
    void simulate_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(simulationService.simulate(eq(10L), eq(1L), any())).thenReturn(
                SimulationResult.builder()
                        .totalEvaluated(50).changedCount(10).changePercentage(20.0)
                        .improvedCount(8).worsenedCount(2)
                        .avgScoreBefore(5.0).avgScoreAfter(6.0).avgScoreDelta(1.0)
                        .sampleDiffs(List.of()).aiAnalysis("Bonne modification.").aiAvailable(true)
                        .build());

        mockMvc.perform(post("/api/ruleset/10/simulate")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ruleId", 1,
                                "sampleSize", 50,
                                "proposedConditions", List.of()))))
                .andExpect(status().isOk());
    }
}
