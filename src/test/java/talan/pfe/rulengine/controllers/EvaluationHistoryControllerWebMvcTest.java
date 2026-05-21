package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.EvaluationDetailResponse;
import talan.pfe.rulengine.dtos.response.EvaluationHistoryResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.EvaluationHistoryService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EvaluationHistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvaluationHistoryControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean EvaluationHistoryService evaluationHistoryService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("10");
    }

    private EvaluationHistoryResponse historyResp(Long id) {
        return EvaluationHistoryResponse.builder()
                .id(id).ruleSetId(5L).ruleSetName("CreditRule")
                .strategyUsed(EvaluationStrategy.FIRST_MATCH)
                .totalScore(85.0).matchedRulesCount(2)
                .tenantId(10L).tenantName("Acme")
                .requestedAt(LocalDateTime.now())
                .evaluatedAt(LocalDateTime.now())
                .build();
    }

    private PageResponse<EvaluationHistoryResponse> pageOf(EvaluationHistoryResponse... items) {
        return PageResponse.<EvaluationHistoryResponse>builder()
                .content(List.of(items)).page(0).size(20)
                .totalElements(items.length).totalPages(1)
                .last(true).build();
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithHistory() throws Exception {
        stubTenant();
        when(evaluationHistoryService.getAll(eq(10L), any(), any(), any(), any()))
                .thenReturn(pageOf(historyResp(1L), historyResp(2L)));

        mockMvc.perform(get("/api/evaluations/history").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].ruleSetName").value("CreditRule"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getAll_withRuleSetIdFilter_returns200() throws Exception {
        stubTenant();
        when(evaluationHistoryService.getAll(eq(10L), eq(5L), any(), any(), any()))
                .thenReturn(pageOf(historyResp(1L)));

        mockMvc.perform(get("/api/evaluations/history")
                        .param("ruleSetId", "5")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ruleSetId").value(5));
    }

    @Test
    void getAll_returnsEmptyPageWhenNoHistory() throws Exception {
        stubTenant();
        when(evaluationHistoryService.getAll(any(), any(), any(), any(), any()))
                .thenReturn(pageOf());

        mockMvc.perform(get("/api/evaluations/history").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200WithDetail() throws Exception {
        stubTenant();
        EvaluationDetailResponse detail = EvaluationDetailResponse.builder()
                .id(1L).ruleSetId(5L).ruleSetName("CreditRule")
                .strategyUsed(EvaluationStrategy.FIRST_MATCH)
                .totalScore(85.0).tenantId(10L)
                .inputPayload("{\"income\": 60000}")
                .outputPayload("{\"result\": \"approved\"}")
                .matchedRules("[{\"ruleId\": 1}]")
                .requestedAt(LocalDateTime.now())
                .build();

        when(evaluationHistoryService.getById(1L, 10L)).thenReturn(detail);

        mockMvc.perform(get("/api/evaluations/history/1").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ruleSetName").value("CreditRule"))
                .andExpect(jsonPath("$.inputPayload").value("{\"income\": 60000}"))
                .andExpect(jsonPath("$.outputPayload").value("{\"result\": \"approved\"}"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        stubTenant();
        when(evaluationHistoryService.getById(999L, 10L))
                .thenThrow(new ResourceNotFoundException("Evaluation not found with id: 999"));

        mockMvc.perform(get("/api/evaluations/history/999").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Evaluation not found with id: 999"));
    }
}