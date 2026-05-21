package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
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
class RuleConditionControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RuleConditionService conditionService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("10");
    }

    private RuleConditionResponse conditionResp(Long id) {
        return RuleConditionResponse.builder()
                .id(id).field("income")
                .operator(Operator.GREATER_THAN)
                .value("50000").valueType(DataType.NUMBER).build();
    }

    private Map<String, Object> conditionBody() {
        return Map.of("field", "income", "operator", "GREATER_THAN",
                "value", "50000", "valueType", "NUMBER");
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_returns201WhenValid() throws Exception {
        stubTenant();
        when(conditionService.create(eq(1L), eq(2L), eq(10L), any())).thenReturn(conditionResp(7L));

        mockMvc.perform(post("/api/rulesets/1/rules/2/conditions")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conditionBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.field").value("income"));
    }

    @Test
    void create_returns400WhenRuleSetArchived() throws Exception {
        stubTenant();
        when(conditionService.create(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("Cannot modify conditions of an archived RuleSet"));

        mockMvc.perform(post("/api/rulesets/1/rules/2/conditions")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conditionBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot modify conditions of an archived RuleSet"));
    }

    @Test
    void create_returns404WhenRuleSetNotFound() throws Exception {
        stubTenant();
        when(conditionService.create(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleSet not found with id: 1"));

        mockMvc.perform(post("/api/rulesets/1/rules/2/conditions")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conditionBody())))
                .andExpect(status().isNotFound());
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithList() throws Exception {
        stubTenant();
        when(conditionService.getAll(1L, 2L, 10L))
                .thenReturn(List.of(conditionResp(1L), conditionResp(2L)));

        mockMvc.perform(get("/api/rulesets/1/rules/2/conditions")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAll_returns404WhenRuleNotFound() throws Exception {
        stubTenant();
        when(conditionService.getAll(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Rule not found with id: 2"));

        mockMvc.perform(get("/api/rulesets/1/rules/2/conditions")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200WithCondition() throws Exception {
        stubTenant();
        when(conditionService.getById(1L, 2L, 7L, 10L)).thenReturn(conditionResp(7L));

        mockMvc.perform(get("/api/rulesets/1/rules/2/conditions/7")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.field").value("income"));
    }

    @Test
    void getById_returns404WhenConditionNotFound() throws Exception {
        stubTenant();
        when(conditionService.getById(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleCondition not found with id: 7"));

        mockMvc.perform(get("/api/rulesets/1/rules/2/conditions/7")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_returns200WithUpdatedCondition() throws Exception {
        stubTenant();
        RuleConditionResponse updated = RuleConditionResponse.builder()
                .id(7L).field("age").operator(Operator.LESS_THAN)
                .value("30").valueType(DataType.NUMBER).build();
        when(conditionService.update(eq(1L), eq(2L), eq(7L), eq(10L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/rulesets/1/rules/2/conditions/7")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("field", "age", "operator", "LESS_THAN",
                                        "value", "30", "valueType", "NUMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.field").value("age"));
    }

    @Test
    void update_returns400WhenRuleSetArchived() throws Exception {
        stubTenant();
        when(conditionService.update(any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException("Cannot modify conditions of an archived RuleSet"));

        mockMvc.perform(put("/api/rulesets/1/rules/2/conditions/7")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conditionBody())))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_returns204OnSuccess() throws Exception {
        stubTenant();
        doNothing().when(conditionService).delete(1L, 2L, 7L, 10L);

        mockMvc.perform(delete("/api/rulesets/1/rules/2/conditions/7")
                        .header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns400WhenRuleSetArchived() throws Exception {
        stubTenant();
        doThrow(new BadRequestException("Cannot delete conditions from an archived RuleSet"))
                .when(conditionService).delete(any(), any(), any(), any());

        mockMvc.perform(delete("/api/rulesets/1/rules/2/conditions/7")
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        stubTenant();
        doThrow(new ResourceNotFoundException("RuleCondition not found with id: 7"))
                .when(conditionService).delete(any(), any(), any(), any());

        mockMvc.perform(delete("/api/rulesets/1/rules/2/conditions/7")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }
}