package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.EvaluationDetailResponse;
import talan.pfe.rulengine.dtos.response.EvaluationHistoryResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.EvaluationHistoryService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EvaluationHistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EvaluationHistoryController")
class EvaluationHistoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean EvaluationHistoryService evaluationHistoryService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test @DisplayName("GET /api/evaluations/history → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(evaluationHistoryService.getAll(any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.<EvaluationHistoryResponse>builder()
                        .content(List.of()).page(0).size(20).totalElements(0).totalPages(0).last(true).build());

        mockMvc.perform(get("/api/evaluations/history")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/evaluations/history/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(evaluationHistoryService.getById(100L, 1L)).thenReturn(
                EvaluationDetailResponse.builder().id(100L).build());

        mockMvc.perform(get("/api/evaluations/history/100")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }
}
