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
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleConditionService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RuleConditionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RuleConditionController")
class RuleConditionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean RuleConditionService conditionService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private RuleConditionResponse stub() {
        return RuleConditionResponse.builder().id(3L)
                .field("amount").operator(Operator.GREATER_THAN)
                .value("1000").valueType(DataType.NUMBER).build();
    }

    @Test @DisplayName("POST /api/rulesets/{id}/rules/{ruleId}/conditions → 201 CREATED")
    void create_returns201() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(conditionService.create(eq(10L), eq(1L), eq(1L), any())).thenReturn(stub());

        mockMvc.perform(post("/api/rulesets/10/rules/1/conditions")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("field", "amount", "operator", "GREATER_THAN",
                                        "value", "1000", "valueType", "NUMBER"))))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/rules/{ruleId}/conditions → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(conditionService.getAll(10L, 1L, 1L)).thenReturn(List.of(stub()));

        mockMvc.perform(get("/api/rulesets/10/rules/1/conditions")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/rules/{ruleId}/conditions/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(conditionService.getById(10L, 1L, 3L, 1L)).thenReturn(stub());

        mockMvc.perform(get("/api/rulesets/10/rules/1/conditions/3")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/rulesets/{id}/rules/{ruleId}/conditions/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        doNothing().when(conditionService).delete(10L, 1L, 3L, 1L);

        mockMvc.perform(delete("/api/rulesets/10/rules/1/conditions/3")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isNoContent());
    }
}
