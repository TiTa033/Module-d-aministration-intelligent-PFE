package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.AiInsightService;
import talan.pfe.rulengine.services.serviceImpl.DocumentationPdfService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AiInsightController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiInsightControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AiInsightService aiInsightService;
    @MockBean DocumentationPdfService documentationPdfService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("1");
    }

    private AiInsightResponse insight(Long id, String title) {
        return AiInsightResponse.builder()
                .id(id).title(title).description("Desc")
                .status(InsightStatus.PENDING)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ─── LIST DOCUMENTATIONS ─────────────────────────────────

    @Test
    void listDocumentation_returns200WithList() throws Exception {
        stubTenant();
        when(aiInsightService.getRuleSetDocumentation(5L, 1L))
                .thenReturn(List.of(insight(1L, "Doc - RuleSet RS")));

        mockMvc.perform(get("/api/rulesets/5/insights/documentations")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Doc - RuleSet RS"));
    }

    @Test
    void listDocumentation_whenRuleSetNotFound_returns404() throws Exception {
        stubTenant();
        when(aiInsightService.getRuleSetDocumentation(any(), any()))
                .thenThrow(new ResourceNotFoundException("RuleSet not found"));

        mockMvc.perform(get("/api/rulesets/999/insights/documentations")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── LATEST DOCUMENTATION ────────────────────────────────

    @Test
    void latestDocumentation_returns200() throws Exception {
        stubTenant();
        when(aiInsightService.getLatestRuleSetDocumentation(5L, 1L))
                .thenReturn(insight(2L, "Latest Doc"));

        mockMvc.perform(get("/api/rulesets/5/insights/latest-documentation")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.title").value("Latest Doc"));
    }

    @Test
    void latestDocumentation_whenNoDocumentationFound_returns404() throws Exception {
        stubTenant();
        when(aiInsightService.getLatestRuleSetDocumentation(any(), any()))
                .thenThrow(new ResourceNotFoundException("Aucune documentation IA trouvée."));

        mockMvc.perform(get("/api/rulesets/5/insights/latest-documentation")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Aucune documentation IA trouvée."));
    }

    // ─── GENERATE ────────────────────────────────────────────

    @Test
    void generateNow_returns202Accepted() throws Exception {
        stubTenant();
        doNothing().when(aiInsightService).generateDocumentationNow(5L, 1L);

        mockMvc.perform(post("/api/rulesets/5/insights/generate")
                        .header("Authorization", AUTH))
                .andExpect(status().isAccepted());
    }

    @Test
    void generateNow_whenRuleSetNotFound_returns404() throws Exception {
        stubTenant();
        doThrow(new ResourceNotFoundException("RuleSet not found"))
                .when(aiInsightService).generateDocumentationNow(any(), any());

        mockMvc.perform(post("/api/rulesets/999/insights/generate")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── UPDATE STATUS ────────────────────────────────────────

    @Test
    void updateStatus_toAccepted_returns200() throws Exception {
        AiInsightResponse updated = insight(1L, "Doc");
        updated.setStatus(InsightStatus.ACCEPTED);
        when(aiInsightService.updateStatus(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/insights/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void updateStatus_toRejected_returns200() throws Exception {
        AiInsightResponse updated = insight(1L, "Doc");
        updated.setStatus(InsightStatus.REJECTED);
        when(aiInsightService.updateStatus(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/insights/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void updateStatus_whenInsightNotFound_returns404() throws Exception {
        when(aiInsightService.updateStatus(any(), any()))
                .thenThrow(new ResourceNotFoundException("Insight introuvable."));

        mockMvc.perform(patch("/api/insights/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_withInvalidStatus_returns400() throws Exception {
        when(aiInsightService.updateStatus(any(), any()))
                .thenThrow(new BadRequestException("Seuls ACCEPTED ou REJECTED sont autorisés."));

        mockMvc.perform(patch("/api/insights/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PENDING"))))
                .andExpect(status().isBadRequest());
    }

    // ─── PDF ─────────────────────────────────────────────────

    @Test
    void latestDocumentationPdf_returns200WithPdfContentType() throws Exception {
        stubTenant();
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF magic bytes
        when(documentationPdfService.buildRuleSetDocumentationPdf(5L, 1L)).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/rulesets/5/insights/latest-documentation/pdf")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void engineGuidePdf_returns200WithPdfContentType() throws Exception {
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46};
        when(documentationPdfService.buildEngineGuidePdf()).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/documentation/engine-guide/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }
}