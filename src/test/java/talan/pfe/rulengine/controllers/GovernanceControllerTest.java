package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.GovernanceSummaryResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.GovernanceService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GovernanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GovernanceController")
class GovernanceControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean GovernanceService governanceService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private GovernanceSummaryResponse stubSummary() {
        return GovernanceSummaryResponse.builder()
                .activeRuleSets(3L)
                .totalEvaluationsToday(42L)
                .avgExecutionMsToday(150.0)
                .activeAlerts(2L)
                .unreadNotifications(5L)
                .topRuleSets(List.of())
                .build();
    }

    @Test @DisplayName("GET /api/governance/summary → 200 OK")
    void getSummary_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(governanceService.getSummary(1L)).thenReturn(stubSummary());

        mockMvc.perform(get("/api/governance/summary")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/governance/summary → 200 OK with null tenantId")
    void getSummary_nullTenantId_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn(null);
        when(governanceService.getSummary(null)).thenReturn(stubSummary());

        mockMvc.perform(get("/api/governance/summary")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }
}
