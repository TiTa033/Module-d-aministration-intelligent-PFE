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
import talan.pfe.rulengine.dtos.request.CreateAlertConfigRequest;
import talan.pfe.rulengine.dtos.response.AlertConfigResponse;
import talan.pfe.rulengine.enums.AlertCondition;
import talan.pfe.rulengine.enums.AlertMetric;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.AlertConfigService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AlertConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AlertConfigController")
class AlertConfigControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AlertConfigService alertConfigService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private AlertConfigResponse stubResponse() {
        return AlertConfigResponse.builder()
                .id(1L)
                .name("High Load Alert")
                .metric(AlertMetric.EVALUATION_COUNT)
                .conditionType(AlertCondition.GREATER_THAN)
                .threshold(100.0)
                .windowHours(1)
                .enabled(true)
                .build();
    }

    @Test @DisplayName("POST /api/governance/alerts → 201 CREATED")
    void create_returns201() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(alertConfigService.create(any(), eq(1L))).thenReturn(stubResponse());

        CreateAlertConfigRequest req = new CreateAlertConfigRequest();
        req.setName("High Load Alert");
        req.setMetric(AlertMetric.EVALUATION_COUNT);
        req.setConditionType(AlertCondition.GREATER_THAN);
        req.setThreshold(100.0);
        req.setWindowHours(1);

        mockMvc.perform(post("/api/governance/alerts")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/governance/alerts → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(alertConfigService.getAll(1L)).thenReturn(List.of(stubResponse()));

        mockMvc.perform(get("/api/governance/alerts")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/governance/alerts/{id}/toggle → 200 OK")
    void toggle_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(alertConfigService.toggleEnabled(1L, 1L)).thenReturn(stubResponse());

        mockMvc.perform(patch("/api/governance/alerts/1/toggle")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/governance/alerts/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        doNothing().when(alertConfigService).delete(1L, 1L);

        mockMvc.perform(delete("/api/governance/alerts/1")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("extractTenantId: null tenantId in token returns null")
    void getAll_nullTenantId_returnsOk() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn(null);
        when(alertConfigService.getAll(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/governance/alerts")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }
}
