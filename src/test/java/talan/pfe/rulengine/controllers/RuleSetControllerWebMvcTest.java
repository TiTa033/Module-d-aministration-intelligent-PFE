package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.request.RuleSetImportRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.dtos.response.RuleSetValidationResponse;
import talan.pfe.rulengine.dtos.response.RuleSetVersionResponse;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.RuleSetImportExportService;
import talan.pfe.rulengine.services.RuleSetService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RuleSetController.class)
@AutoConfigureMockMvc(addFilters = false)
class RuleSetControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RuleSetService ruleSetService;
    @MockBean private RuleSetImportExportService ruleSetImportExportService;
    @MockBean private JwtService jwtService;
    @MockBean private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("1");
    }

    private RuleSetResponse rsResp(Long id, String name) {
        return RuleSetResponse.builder().id(id).name(name)
                .evaluationStrategy("FIRST_MATCH").status("ACTIVE").tenantId(1L).build();
    }

    @Test
    void create_returns201() throws Exception {
        stubTenant();
        when(ruleSetService.create(any(CreateRuleSetRequest.class), eq(1L)))
                .thenReturn(rsResp(10L, "RS"));

        mockMvc.perform(post("/api/rulesets")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "RS",
                                "description", "d",
                                "evaluationStrategy", "FIRST_MATCH"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_whenConflict_returns409() throws Exception {
        stubTenant();
        when(ruleSetService.create(any(), any())).thenThrow(new ConflictException("Name taken"));

        mockMvc.perform(post("/api/rulesets")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "RS", "evaluationStrategy", "FIRST_MATCH"))))
                .andExpect(status().isConflict());
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200() throws Exception {
        stubTenant();
        PageResponse<RuleSetResponse> page = PageResponse.<RuleSetResponse>builder()
                .content(List.of(rsResp(1L, "RS1"))).page(0).size(10)
                .totalElements(1).totalPages(1).last(true).build();
        when(ruleSetService.getAll(eq(1L), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/rulesets").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("RS1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200() throws Exception {
        stubTenant();
        when(ruleSetService.getById(10L, 1L)).thenReturn(rsResp(10L, "MyRS"));

        mockMvc.perform(get("/api/rulesets/10").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("MyRS"));
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        stubTenant();
        when(ruleSetService.getById(999L, 1L))
                .thenThrow(new ResourceNotFoundException("RuleSet not found with id: 999"));

        mockMvc.perform(get("/api/rulesets/999").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("RuleSet not found with id: 999"));
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_returns200() throws Exception {
        stubTenant();
        when(ruleSetService.update(eq(10L), eq(1L), any())).thenReturn(rsResp(10L, "Updated"));

        mockMvc.perform(put("/api/rulesets/10")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated", "evaluationStrategy", "ALL_MATCH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    // ─── ACTIVATE / ARCHIVE / UNARCHIVE / DRAFT ───────────────

    @Test
    void activate_returns200() throws Exception {
        stubTenant();
        when(ruleSetService.activate(10L, 1L)).thenReturn(rsResp(10L, "RS"));

        mockMvc.perform(patch("/api/rulesets/10/activate").header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void activate_whenAlreadyActive_returns400() throws Exception {
        stubTenant();
        when(ruleSetService.activate(10L, 1L))
                .thenThrow(new BadRequestException("RuleSet is already ACTIVE"));

        mockMvc.perform(patch("/api/rulesets/10/activate").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("RuleSet is already ACTIVE"));
    }

    @Test
    void archive_returns200() throws Exception {
        stubTenant();
        when(ruleSetService.archive(10L, 1L)).thenReturn(rsResp(10L, "RS"));

        mockMvc.perform(patch("/api/rulesets/10/archive").header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void unarchive_returns200() throws Exception {
        stubTenant();
        when(ruleSetService.unarchive(10L, 1L)).thenReturn(rsResp(10L, "RS"));

        mockMvc.perform(patch("/api/rulesets/10/unarchive").header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void moveToDraft_returns200() throws Exception {
        stubTenant();
        when(ruleSetService.moveToDraft(10L, 1L)).thenReturn(rsResp(10L, "RS"));

        mockMvc.perform(patch("/api/rulesets/10/draft").header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_returns204() throws Exception {
        stubTenant();
        doNothing().when(ruleSetService).delete(10L, 1L);

        mockMvc.perform(delete("/api/rulesets/10").header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenActive_returns400() throws Exception {
        stubTenant();
        doThrow(new BadRequestException("Cannot delete an ACTIVE RuleSet"))
                .when(ruleSetService).delete(10L, 1L);

        mockMvc.perform(delete("/api/rulesets/10").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete an ACTIVE RuleSet"));
    }

    @Test
    void delete_whenNotFound_returns404() throws Exception {
        stubTenant();
        doThrow(new ResourceNotFoundException("RuleSet not found"))
                .when(ruleSetService).delete(999L, 1L);

        mockMvc.perform(delete("/api/rulesets/999").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── EXPORT ──────────────────────────────────────────────

    @Test
    void export_returns200() throws Exception {
        stubTenant();
        when(ruleSetImportExportService.exportJson(10L, 1L))
                .thenReturn("{\"name\":\"RS\"}");

        mockMvc.perform(get("/api/rulesets/10/export").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"name\":\"RS\"}"));
    }

    // ─── IMPORT ──────────────────────────────────────────────

    @Test
    void importPackage_returns201() throws Exception {
        stubTenant();
        when(ruleSetImportExportService.importPackage(any(RuleSetImportRequest.class), eq(1L)))
                .thenReturn(rsResp(20L, "Imported"));

        mockMvc.perform(post("/api/rulesets/import")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "packageJson", Map.of(
                                        "schemaVersion", 1, "name", "RS",
                                        "evaluationStrategy", "FIRST_MATCH",
                                        "rules", List.of()),
                                "failIfNameExists", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20));
    }

    @Test
    void validateImport_returns200() throws Exception {
        stubTenant();
        when(ruleSetImportExportService.validatePackage(any(RuleSetImportRequest.class), eq(1L)))
                .thenReturn(RuleSetValidationResponse.builder()
                        .valid(true).errors(List.of()).build());

        mockMvc.perform(post("/api/rulesets/import/validate")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "packageJson", Map.of(
                                        "schemaVersion", 1, "name", "RS",
                                        "evaluationStrategy", "FIRST_MATCH",
                                        "rules", List.of()),
                                "failIfNameExists", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    // ─── VERSIONS ────────────────────────────────────────────

    @Test
    void listVersions_returns200() throws Exception {
        stubTenant();
        when(ruleSetImportExportService.listVersions(10L, 1L))
                .thenReturn(List.of(RuleSetVersionResponse.builder()
                        .id(1L).versionNumber(1).build()));

        mockMvc.perform(get("/api/rulesets/10/versions").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(1));
    }

    @Test
    void restoreVersion_returns200() throws Exception {
        stubTenant();
        when(ruleSetImportExportService.restoreVersion(eq(10L), eq(1L), eq(2), any()))
                .thenReturn(rsResp(10L, "RS"));

        mockMvc.perform(post("/api/rulesets/10/versions/2/restore")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}

