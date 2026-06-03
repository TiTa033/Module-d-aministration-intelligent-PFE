package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.GlobalAdminDashboardChartsResponse;
import talan.pfe.rulengine.dtos.response.GlobalAdminDashboardResponse;
import talan.pfe.rulengine.dtos.response.LabelCountResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.GlobalAdminDashboardService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalAdminDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GlobalAdminDashboardController")
class GlobalAdminDashboardControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean GlobalAdminDashboardService dashboardService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("GET /api/admin/dashboard → 200 OK")
    void getDashboard_returns200() throws Exception {
        when(dashboardService.getDashboard(30)).thenReturn(
                GlobalAdminDashboardResponse.builder()
                        .totalTenants(3L)
                        .activeTenants(2L)
                        .inactiveTenants(1L)
                        .charts(GlobalAdminDashboardChartsResponse.builder()
                                .trendPeriodDays(30)
                                .tenantsByStatus(List.of(
                                        LabelCountResponse.builder().label("ACTIVE").count(2L).build()))
                                .build())
                        .build());

        mockMvc.perform(get("/api/admin/dashboard").param("trendDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTenants").value(3))
                .andExpect(jsonPath("$.activeTenants").value(2))
                .andExpect(jsonPath("$.inactiveTenants").value(1))
                .andExpect(jsonPath("$.charts.trendPeriodDays").value(30))
                .andExpect(jsonPath("$.charts.tenantsByStatus[0].label").value("ACTIVE"));
    }
}
