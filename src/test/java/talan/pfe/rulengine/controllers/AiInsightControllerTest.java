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
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.AiInsightService;
import talan.pfe.rulengine.services.serviceImpl.DocumentationPdfService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AiInsightController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AiInsightController")
class AiInsightControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AiInsightService aiInsightService;
    @MockBean DocumentationPdfService documentationPdfService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private AiInsightResponse stub() {
        return AiInsightResponse.builder().id(1L).ruleSetId(10L)
                .title("Doc IA").description("Ce RuleSet...")
                .status(InsightStatus.PENDING).build();
    }

    @Test @DisplayName("GET /api/rulesets/{id}/insights/documentations → 200 OK")
    void listDocumentation_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(aiInsightService.getRuleSetDocumentation(10L, 1L)).thenReturn(List.of(stub()));

        mockMvc.perform(get("/api/rulesets/10/insights/documentations")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/rulesets/{id}/insights/latest-documentation → 200 OK")
    void latestDocumentation_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(aiInsightService.getLatestRuleSetDocumentation(10L, 1L)).thenReturn(stub());

        mockMvc.perform(get("/api/rulesets/10/insights/latest-documentation")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/insights/{id}/status → 200 OK")
    void updateStatus_returns200() throws Exception {
        when(aiInsightService.updateStatus(eq(1L), any())).thenReturn(stub());

        mockMvc.perform(patch("/api/insights/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /api/rulesets/{id}/insights/generate → 202 ACCEPTED")
    void generateNow_returns202() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        doNothing().when(aiInsightService).generateDocumentationNow(10L, 1L);

        mockMvc.perform(post("/api/rulesets/10/insights/generate")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isAccepted());
    }

    @Test @DisplayName("GET /api/documentation/engine-guide/pdf → 200 OK")
    void engineGuidePdf_returns200() throws Exception {
        when(documentationPdfService.buildEngineGuidePdf()).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/documentation/engine-guide/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }
}
