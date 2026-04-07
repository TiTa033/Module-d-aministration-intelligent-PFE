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
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.dtos.response.RuleSetValidationResponse;
import talan.pfe.rulengine.dtos.response.RuleSetVersionResponse;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.RuleSetImportExportService;
import talan.pfe.rulengine.services.RuleSetService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
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

    @Test
    void create_returns201() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleSetService.create(any(CreateRuleSetRequest.class), eq(1L)))
                .thenReturn(RuleSetResponse.builder().id(10L).name("RS").build());

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
    void export_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleSetImportExportService.exportJson(10L, 1L))
                .thenReturn("{\"name\":\"RS\"}");

        mockMvc.perform(get("/api/rulesets/10/export")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"name\":\"RS\"}"));
    }

    @Test
    void validateImport_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleSetImportExportService.validatePackage(any(RuleSetImportRequest.class), eq(1L)))
                .thenReturn(RuleSetValidationResponse.builder()
                        .valid(true)
                        .errors(List.of())
                        .build());

        mockMvc.perform(post("/api/rulesets/import/validate")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "packageJson", Map.of(
                                        "schemaVersion", 1,
                                        "name", "RS",
                                        "evaluationStrategy", "FIRST_MATCH",
                                        "rules", List.of()
                                ),
                                "failIfNameExists", true
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void listVersions_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(ruleSetImportExportService.listVersions(10L, 1L))
                .thenReturn(List.of(RuleSetVersionResponse.builder()
                        .id(1L).versionNumber(1).build()));

        mockMvc.perform(get("/api/rulesets/10/versions")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }
}

