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
import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.dtos.response.RuleSetVersionResponse;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleSetImportExportService;
import talan.pfe.rulengine.services.RuleSetService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RuleSetController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RuleSetController")
class RuleSetControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RuleSetService ruleSetService;
    @MockBean RuleSetImportExportService ruleSetImportExportService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private RuleSetResponse stubRuleSet() {
        return RuleSetResponse.builder()
                .id(10L)
                .name("Credit RS")
                .evaluationStrategy("FIRST_MATCH")
                .status("DRAFT")
                .tenantId(1L)
                .build();
    }

    @Test @DisplayName("POST /api/rulesets → 201 CREATED")
    void create_returns201() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetService.create(any(), eq(1L))).thenReturn(stubRuleSet());

        CreateRuleSetRequest req = new CreateRuleSetRequest();
        req.setName("Credit RS");
        req.setEvaluationStrategy(EvaluationStrategy.FIRST_MATCH);

        mockMvc.perform(post("/api/rulesets")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/rulesets → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        PageResponse<RuleSetResponse> page = PageResponse.<RuleSetResponse>builder()
                .content(List.of(stubRuleSet())).totalElements(1).totalPages(1).page(0).size(10).last(true).build();
        when(ruleSetService.getAll(eq(1L), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/rulesets")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/rulesets/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetService.getById(10L, 1L)).thenReturn(stubRuleSet());

        mockMvc.perform(get("/api/rulesets/10")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PUT /api/rulesets/{id} → 200 OK")
    void update_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetService.update(eq(10L), eq(1L), any())).thenReturn(stubRuleSet());

        talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest updateReq =
                new talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest(
                        "Credit RS Updated", "desc", EvaluationStrategy.FIRST_MATCH);

        mockMvc.perform(put("/api/rulesets/10")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/activate → 200 OK")
    void activate_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetService.activate(10L, 1L)).thenReturn(stubRuleSet());

        mockMvc.perform(patch("/api/rulesets/10/activate")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/archive → 200 OK")
    void archive_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetService.archive(10L, 1L)).thenReturn(stubRuleSet());

        mockMvc.perform(patch("/api/rulesets/10/archive")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/draft → 200 OK")
    void moveToDraft_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetService.moveToDraft(10L, 1L)).thenReturn(stubRuleSet());

        mockMvc.perform(patch("/api/rulesets/10/draft")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/rulesets/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        doNothing().when(ruleSetService).delete(10L, 1L);

        mockMvc.perform(delete("/api/rulesets/10")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/versions → 200 OK")
    void listVersions_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetImportExportService.listVersions(10L, 1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/rulesets/10/versions")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/export → 200 OK")
    void export_returns200() throws Exception {
        when(jwtService.extractTenantId("tok")).thenReturn("1");
        when(ruleSetImportExportService.exportJson(10L, 1L)).thenReturn("{\"name\":\"Credit RS\"}");

        mockMvc.perform(get("/api/rulesets/10/export")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }
}
