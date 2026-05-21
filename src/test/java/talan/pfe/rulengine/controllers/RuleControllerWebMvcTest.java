package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.request.CreateRuleRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
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
class RuleControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RuleService ruleService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("1");
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_validRequest_returns201() throws Exception {
        stubTenant();
        RuleResponse resp = RuleResponse.builder().id(10L).name("Rule1").build();
        when(ruleService.create(eq(5L), eq(1L), any(CreateRuleRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/rulesets/5/rules")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rule1",
                                "priority", 1,
                                "logicOperator", "AND"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Rule1"));
    }

    @Test
    void create_whenDuplicateName_returns409() throws Exception {
        stubTenant();
        when(ruleService.create(any(), any(), any())).thenThrow(new ConflictException("Duplicate"));

        mockMvc.perform(post("/api/rulesets/5/rules")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "DupRule", "priority", 1, "logicOperator", "AND"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void create_whenArchivedRuleSet_returns400() throws Exception {
        stubTenant();
        when(ruleService.create(any(), any(), any()))
                .thenThrow(new BadRequestException("Cannot add rules to an archived RuleSet"));

        mockMvc.perform(post("/api/rulesets/5/rules")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rx", "priority", 1, "logicOperator", "AND"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot add rules to an archived RuleSet"));
    }

    @Test
    void create_whenRuleSetNotFound_returns404() throws Exception {
        stubTenant();
        when(ruleService.create(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleSet not found with id: 999"));

        mockMvc.perform(post("/api/rulesets/999/rules")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rx", "priority", 1, "logicOperator", "AND"
                        ))))
                .andExpect(status().isNotFound());
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithContent() throws Exception {
        stubTenant();
        PageResponse<RuleResponse> page = PageResponse.<RuleResponse>builder()
                .content(List.of(RuleResponse.builder().id(1L).name("R1").build()))
                .page(0).size(10).totalElements(1).totalPages(1)
                .last(true).build();
        when(ruleService.getAll(eq(5L), eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/rulesets/5/rules")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("R1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAll_whenRuleSetNotFound_returns404() throws Exception {
        stubTenant();
        when(ruleService.getAll(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleSet not found"));

        mockMvc.perform(get("/api/rulesets/999/rules")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── GET LIST ────────────────────────────────────────────

    @Test
    void getAllList_returns200() throws Exception {
        stubTenant();
        when(ruleService.getAllList(5L, 1L)).thenReturn(List.of(
                RuleResponse.builder().id(1L).priority(1).build()));

        mockMvc.perform(get("/api/rulesets/5/rules/list")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].priority").value(1));
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200() throws Exception {
        stubTenant();
        when(ruleService.getById(5L, 10L, 1L))
                .thenReturn(RuleResponse.builder().id(10L).name("MyRule").build());

        mockMvc.perform(get("/api/rulesets/5/rules/10")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MyRule"));
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        stubTenant();
        when(ruleService.getById(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Rule not found"));

        mockMvc.perform(get("/api/rulesets/5/rules/999")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_returns200() throws Exception {
        stubTenant();
        when(ruleService.update(eq(5L), eq(10L), eq(1L), any(UpdateRuleRequest.class)))
                .thenReturn(RuleResponse.builder().id(10L).name("Updated").build());

        mockMvc.perform(put("/api/rulesets/5/rules/10")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated", "priority", 1, "logicOperator", "AND"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void update_whenConflict_returns409() throws Exception {
        stubTenant();
        when(ruleService.update(any(), any(), any(), any()))
                .thenThrow(new ConflictException("Name conflict"));

        mockMvc.perform(put("/api/rulesets/5/rules/10")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "TakenName", "priority", 1, "logicOperator", "AND"
                        ))))
                .andExpect(status().isConflict());
    }

    // ─── ENABLE / DISABLE ────────────────────────────────────

    @Test
    void enable_returns200() throws Exception {
        stubTenant();
        when(ruleService.enable(5L, 10L, 1L))
                .thenReturn(RuleResponse.builder().id(10L).build());

        mockMvc.perform(patch("/api/rulesets/5/rules/10/enable")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void enable_whenAlreadyEnabled_returns400() throws Exception {
        stubTenant();
        when(ruleService.enable(any(), any(), any()))
                .thenThrow(new BadRequestException("Rule is already enabled"));

        mockMvc.perform(patch("/api/rulesets/5/rules/10/enable")
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rule is already enabled"));
    }

    @Test
    void disable_returns200() throws Exception {
        stubTenant();
        when(ruleService.disable(5L, 10L, 1L))
                .thenReturn(RuleResponse.builder().id(10L).build());

        mockMvc.perform(patch("/api/rulesets/5/rules/10/disable")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void disable_whenAlreadyDisabled_returns400() throws Exception {
        stubTenant();
        when(ruleService.disable(any(), any(), any()))
                .thenThrow(new BadRequestException("Rule is already disabled"));

        mockMvc.perform(patch("/api/rulesets/5/rules/10/disable")
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_returns204() throws Exception {
        stubTenant();
        doNothing().when(ruleService).delete(5L, 10L, 1L);

        mockMvc.perform(delete("/api/rulesets/5/rules/10")
                        .header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenArchivedRuleSet_returns400() throws Exception {
        stubTenant();
        doThrow(new BadRequestException("Cannot delete rules from an archived RuleSet"))
                .when(ruleService).delete(any(), any(), any());

        mockMvc.perform(delete("/api/rulesets/5/rules/10")
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_whenNotFound_returns404() throws Exception {
        stubTenant();
        doThrow(new ResourceNotFoundException("Rule not found"))
                .when(ruleService).delete(any(), any(), any());

        mockMvc.perform(delete("/api/rulesets/5/rules/10")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }
}