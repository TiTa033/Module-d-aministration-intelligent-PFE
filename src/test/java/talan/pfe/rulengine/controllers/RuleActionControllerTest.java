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
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleActionService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RuleActionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RuleActionController")
class RuleActionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean RuleActionService actionService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private RuleActionResponse stub() {
        return RuleActionResponse.builder().id(5L).actionType(ActionType.SET_VALUE)
                .outputKey("decision").outputValue("APPROVED").build();
    }

    @Test @DisplayName("POST /api/rulesets/{id}/rules/{ruleId}/actions → 201 CREATED")
    void create_returns201() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(actionService.create(eq(10L), eq(1L), eq(1L), any())).thenReturn(stub());

        mockMvc.perform(post("/api/rulesets/10/rules/1/actions")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("actionType", "SET_VALUE", "outputKey", "decision", "outputValue", "APPROVED"))))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/rules/{ruleId}/actions → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(actionService.getAll(10L, 1L, 1L)).thenReturn(List.of(stub()));

        mockMvc.perform(get("/api/rulesets/10/rules/1/actions")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/rules/{ruleId}/actions/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(actionService.getById(10L, 1L, 5L, 1L)).thenReturn(stub());

        mockMvc.perform(get("/api/rulesets/10/rules/1/actions/5")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PUT /api/rulesets/{id}/rules/{ruleId}/actions/{id} → 200 OK")
    void update_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(actionService.update(eq(10L), eq(1L), eq(5L), eq(1L), any())).thenReturn(stub());

        mockMvc.perform(put("/api/rulesets/10/rules/1/actions/5")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("actionType", "APPROVE", "outputKey", "decision", "outputValue", "APPROVED"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/rulesets/{id}/rules/{ruleId}/actions/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        doNothing().when(actionService).delete(10L, 1L, 5L, 1L);

        mockMvc.perform(delete("/api/rulesets/10/rules/1/actions/5")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isNoContent());
    }
}
