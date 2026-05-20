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
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RuleController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RuleController")
class RuleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RuleService ruleService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private RuleResponse stub() {
        return RuleResponse.builder().id(1L).name("High Income").priority(1).enabled(true).build();
    }

    @Test @DisplayName("POST /api/rulesets/{id}/rules → 201 CREATED")
    void create_returns201() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleService.create(eq(10L), eq(1L), any())).thenReturn(stub());

        mockMvc.perform(post("/api/rulesets/10/rules")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "High Income", "priority", 1, "logicOperator", "AND"))))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/rules/list → 200 OK")
    void getAllList_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleService.getAllList(10L, 1L)).thenReturn(List.of(stub()));

        mockMvc.perform(get("/api/rulesets/10/rules/list")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/rules/{ruleId} → 200 OK")
    void getById_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleService.getById(10L, 1L, 1L)).thenReturn(stub());

        mockMvc.perform(get("/api/rulesets/10/rules/1")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/rules/{ruleId}/enable → 200 OK")
    void enable_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleService.enable(10L, 1L, 1L)).thenReturn(stub());

        mockMvc.perform(patch("/api/rulesets/10/rules/1/enable")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/rules/{ruleId}/disable → 200 OK")
    void disable_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleService.disable(10L, 1L, 1L)).thenReturn(stub());

        mockMvc.perform(patch("/api/rulesets/10/rules/1/disable")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/rulesets/{id}/rules/{ruleId} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        doNothing().when(ruleService).delete(10L, 1L, 1L);

        mockMvc.perform(delete("/api/rulesets/10/rules/1")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isNoContent());
    }
}
