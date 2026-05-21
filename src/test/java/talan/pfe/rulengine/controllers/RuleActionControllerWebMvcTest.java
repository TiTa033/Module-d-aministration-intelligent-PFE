package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
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
class RuleActionControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RuleActionService actionService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("10");
    }

    private RuleActionResponse actionResp(Long id) {
        return RuleActionResponse.builder()
                .id(id).actionType(ActionType.SET_VALUE)
                .outputKey("result").outputValue("approved").build();
    }

    private Map<String, Object> actionBody() {
        return Map.of("actionType", "SET_VALUE", "outputKey", "result", "outputValue", "approved");
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_returns201WhenValid() throws Exception {
        stubTenant();
        when(actionService.create(eq(1L), eq(2L), eq(10L), any())).thenReturn(actionResp(99L));

        mockMvc.perform(post("/api/rulesets/1/rules/2/actions")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.outputKey").value("result"));
    }

    @Test
    void create_returns400WhenRuleSetArchived() throws Exception {
        stubTenant();
        when(actionService.create(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("Cannot modify actions of an archived RuleSet"));

        mockMvc.perform(post("/api/rulesets/1/rules/2/actions")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot modify actions of an archived RuleSet"));
    }

    @Test
    void create_returns404WhenRuleSetNotFound() throws Exception {
        stubTenant();
        when(actionService.create(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleSet not found with id: 1"));

        mockMvc.perform(post("/api/rulesets/1/rules/2/actions")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionBody())))
                .andExpect(status().isNotFound());
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithList() throws Exception {
        stubTenant();
        when(actionService.getAll(1L, 2L, 10L))
                .thenReturn(List.of(actionResp(1L), actionResp(2L)));

        mockMvc.perform(get("/api/rulesets/1/rules/2/actions")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void getAll_returns404WhenRuleNotFound() throws Exception {
        stubTenant();
        when(actionService.getAll(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Rule not found with id: 2"));

        mockMvc.perform(get("/api/rulesets/1/rules/2/actions")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200WithAction() throws Exception {
        stubTenant();
        when(actionService.getById(1L, 2L, 5L, 10L)).thenReturn(actionResp(5L));

        mockMvc.perform(get("/api/rulesets/1/rules/2/actions/5")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void getById_returns404WhenActionNotFound() throws Exception {
        stubTenant();
        when(actionService.getById(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleAction not found with id: 5"));

        mockMvc.perform(get("/api/rulesets/1/rules/2/actions/5")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("RuleAction not found with id: 5"));
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_returns200WithUpdatedAction() throws Exception {
        stubTenant();
        RuleActionResponse updated = actionResp(5L);
        updated.setOutputValue("rejected");
        when(actionService.update(eq(1L), eq(2L), eq(5L), eq(10L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/rulesets/1/rules/2/actions/5")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("actionType", "SET_VALUE", "outputKey", "result", "outputValue", "rejected"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outputValue").value("rejected"));
    }

    @Test
    void update_returns400WhenRuleSetArchived() throws Exception {
        stubTenant();
        when(actionService.update(any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException("Cannot modify actions of an archived RuleSet"));

        mockMvc.perform(put("/api/rulesets/1/rules/2/actions/5")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionBody())))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_returns204OnSuccess() throws Exception {
        stubTenant();
        doNothing().when(actionService).delete(1L, 2L, 5L, 10L);

        mockMvc.perform(delete("/api/rulesets/1/rules/2/actions/5")
                        .header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns400WhenRuleSetArchived() throws Exception {
        stubTenant();
        doThrow(new BadRequestException("Cannot delete actions from an archived RuleSet"))
                .when(actionService).delete(any(), any(), any(), any());

        mockMvc.perform(delete("/api/rulesets/1/rules/2/actions/5")
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        stubTenant();
        doThrow(new ResourceNotFoundException("RuleAction not found with id: 5"))
                .when(actionService).delete(any(), any(), any(), any());

        mockMvc.perform(delete("/api/rulesets/1/rules/2/actions/5")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }
}