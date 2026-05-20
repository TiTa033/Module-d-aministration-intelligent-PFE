package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.*;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleSetImportExportService;
import talan.pfe.rulengine.services.RuleSetService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RuleSetController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RuleSetController")
class RuleSetControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RuleSetService ruleSetService;
    @MockBean RuleSetImportExportService ruleSetImportExportService;
    @MockBean JwtService jwtService;

    private static final String AUTH = "Bearer test-token";
    private RuleSetResponse stubRuleSet;

    @BeforeEach
    void setUp() {
        when(jwtService.extractTenantId("test-token")).thenReturn("1");
        stubRuleSet = RuleSetResponse.builder()
                .id(10L).name("Credit Scoring").build();
    }

    @Test @DisplayName("POST /api/rulesets → 201 CREATED")
    void create_returns201() throws Exception {
        when(ruleSetService.create(any(), eq(1L))).thenReturn(stubRuleSet);

        mockMvc.perform(post("/api/rulesets")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Credit Scoring","description":"desc",
                                 "evaluationStrategy":"FIRST_MATCH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test @DisplayName("GET /api/rulesets → 200 OK")
    void getAll_returns200() throws Exception {
        PageResponse<RuleSetResponse> page = PageResponse.<RuleSetResponse>builder()
                .content(List.of(stubRuleSet)).totalElements(1).totalPages(1)
                .page(0).size(10).build();
        when(ruleSetService.getAll(eq(1L), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/rulesets")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    @Test @DisplayName("GET /api/rulesets/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(ruleSetService.getById(10L, 1L)).thenReturn(stubRuleSet);

        mockMvc.perform(get("/api/rulesets/10")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test @DisplayName("PUT /api/rulesets/{id} → 200 OK")
    void update_returns200() throws Exception {
        when(ruleSetService.update(eq(10L), eq(1L), any())).thenReturn(stubRuleSet);

        mockMvc.perform(put("/api/rulesets/10")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated","description":"d",
                                 "evaluationStrategy":"ALL_MATCH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/activate → 200 OK")
    void activate_returns200() throws Exception {
        when(ruleSetService.activate(10L, 1L)).thenReturn(stubRuleSet);

        mockMvc.perform(patch("/api/rulesets/10/activate")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/archive → 200 OK")
    void archive_returns200() throws Exception {
        when(ruleSetService.archive(10L, 1L)).thenReturn(stubRuleSet);

        mockMvc.perform(patch("/api/rulesets/10/archive")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/unarchive → 200 OK")
    void unarchive_returns200() throws Exception {
        when(ruleSetService.unarchive(10L, 1L)).thenReturn(stubRuleSet);

        mockMvc.perform(patch("/api/rulesets/10/unarchive")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/rulesets/{id}/draft → 200 OK")
    void moveToDraft_returns200() throws Exception {
        when(ruleSetService.moveToDraft(10L, 1L)).thenReturn(stubRuleSet);

        mockMvc.perform(patch("/api/rulesets/10/draft")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/rulesets/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        doNothing().when(ruleSetService).delete(10L, 1L);

        mockMvc.perform(delete("/api/rulesets/10")
                        .header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/export → 200 OK with JSON")
    void export_returns200() throws Exception {
        when(ruleSetImportExportService.exportJson(10L, 1L))
                .thenReturn("{\"name\":\"Credit Scoring\"}");

        mockMvc.perform(get("/api/rulesets/10/export")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test @DisplayName("POST /api/rulesets/import → 201 CREATED")
    void importPackage_returns201() throws Exception {
        when(ruleSetImportExportService.importPackage(any(), eq(1L)))
                .thenReturn(stubRuleSet);

        mockMvc.perform(post("/api/rulesets/import")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"json\":\"{}\"}"))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("POST /api/rulesets/import/validate → 200 OK")
    void validateImport_returns200() throws Exception {
        RuleSetValidationResponse validation = RuleSetValidationResponse.builder()
                .valid(true).build();
        when(ruleSetImportExportService.validatePackage(any(), eq(1L)))
                .thenReturn(validation);

        mockMvc.perform(post("/api/rulesets/import/validate")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"json\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test @DisplayName("GET /api/rulesets/{id}/versions → 200 OK")
    void listVersions_returns200() throws Exception {
        RuleSetVersionResponse v = mock(RuleSetVersionResponse.class);
        when(ruleSetImportExportService.listVersions(10L, 1L))
                .thenReturn(List.of(v));

        mockMvc.perform(get("/api/rulesets/10/versions")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /api/rulesets/{id}/versions/{version}/restore → 200 OK")
    void restoreVersion_returns200() throws Exception {
        when(ruleSetImportExportService.restoreVersion(eq(10L), eq(1L), eq(2), any()))
                .thenReturn(stubRuleSet);

        mockMvc.perform(post("/api/rulesets/10/versions/2/restore")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("getTenantId returns null when tenant is 'null' string")
    void getTenantId_nullString_returnsNull() throws Exception {
        when(jwtService.extractTenantId("test-token")).thenReturn("null");
        when(ruleSetService.getById(10L, null)).thenReturn(stubRuleSet);

        mockMvc.perform(get("/api/rulesets/10")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }
}