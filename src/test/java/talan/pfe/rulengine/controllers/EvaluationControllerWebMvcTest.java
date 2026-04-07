package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.dtos.request.EvaluateRequest;
import talan.pfe.rulengine.dtos.response.EvaluateResponse;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.security.ApiClientPrincipal;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.EvaluationService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EvaluationController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvaluationControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EvaluationService evaluationService;
    @MockBean private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void evaluate_returns200() throws Exception {
        when(evaluationService.evaluate(any(EvaluateRequest.class), any(ApiClientPrincipal.class)))
                .thenReturn(EvaluateResponse.builder()
                        .evaluationRequestId(1L)
                        .ruleSetId(10L)
                        .ruleSetName("RS")
                        .strategyUsed(EvaluationStrategy.FIRST_MATCH)
                        .output(objectMapper.valueToTree(Map.of("decision", "APPROVED")))
                        .matchedRules(objectMapper.valueToTree(List.of(Map.of("name", "R1"))))
                        .totalScore(0.0)
                        .executionTimeMs(5)
                        .build());

        ApiClientPrincipal principal = new ApiClientPrincipal(1L, 2L);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        mockMvc.perform(post("/api/evaluate")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "input", Map.of("amount", 150)
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void evaluate_missingBodyFields_returns400() throws Exception {
        mockMvc.perform(post("/api/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

