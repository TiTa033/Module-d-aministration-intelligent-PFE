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
import talan.pfe.rulengine.dtos.request.InsightStatusUpdateRequest;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnomalyDetectionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AnomalyDetectionController")
class AnomalyDetectionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AiInsightRepository aiInsightRepository;
    @MockBean CurrentUserResolver currentUserResolver;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private Tenant tenant;
    private User user;
    private AiInsight insight;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        user = User.builder().id(2L).email("admin@bank.com").role(Role.ADMIN).tenant(tenant).build();
        insight = AiInsight.builder()
                .id(1L)
                .title("Anomaly Found")
                .description("Suspicious pattern detected")
                .type(InsightType.ANOMALY)
                .agentType(AgentType.ANOMALY_DETECTION_AGENT)
                .status(InsightStatus.PENDING)
                .confidence(0.9f)
                .tenant(tenant)
                .build();
    }

    @Test @DisplayName("GET /api/anomalies → 200 OK")
    void getAnomalies_returns200() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findByTenantIdAndAgentTypeOrderByGeneratedAtDesc(
                1L, AgentType.ANOMALY_DETECTION_AGENT))
                .thenReturn(List.of(insight));

        mockMvc.perform(get("/api/anomalies"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/anomalies → 400 when user has no tenant")
    void getAnomalies_noTenant_returns400() throws Exception {
        User noTenantUser = User.builder().id(3L).email("global@raas.com")
                .role(Role.GLOBAL_ADMIN).tenant(null).build();
        when(currentUserResolver.requireUser()).thenReturn(noTenantUser);

        mockMvc.perform(get("/api/anomalies"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("GET /api/anomalies/latest → 200 OK")
    void latestAnomaly_returns200() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findFirstByTenantIdAndAgentTypeOrderByGeneratedAtDesc(
                1L, AgentType.ANOMALY_DETECTION_AGENT))
                .thenReturn(Optional.of(insight));

        mockMvc.perform(get("/api/anomalies/latest"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/anomalies/latest → 404 when no anomaly exists")
    void latestAnomaly_notFound_returns404() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findFirstByTenantIdAndAgentTypeOrderByGeneratedAtDesc(
                1L, AgentType.ANOMALY_DETECTION_AGENT))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/anomalies/latest"))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("PATCH /api/anomalies/{id}/status → 200 OK (ACCEPTED)")
    void updateStatus_accepted_returns200() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));
        when(aiInsightRepository.save(any())).thenReturn(insight);

        mockMvc.perform(patch("/api/anomalies/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/anomalies/{id}/status → 200 OK (REJECTED)")
    void updateStatus_rejected_returns200() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));
        when(aiInsightRepository.save(any())).thenReturn(insight);

        mockMvc.perform(patch("/api/anomalies/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/anomalies/{id}/status → 404 when insight not found")
    void updateStatus_notFound_returns404() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/anomalies/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("PATCH /api/anomalies/{id}/status → 400 when insight belongs to different tenant")
    void updateStatus_differentTenant_returns404() throws Exception {
        Tenant otherTenant = Tenant.builder().id(99L).name("OtherCorp").build();
        AiInsight otherInsight = AiInsight.builder()
                .id(1L).title("Other").description("Other")
                .type(InsightType.ANOMALY)
                .agentType(AgentType.ANOMALY_DETECTION_AGENT)
                .status(InsightStatus.PENDING)
                .confidence(0.5f)
                .tenant(otherTenant)
                .build();

        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(otherInsight));

        mockMvc.perform(patch("/api/anomalies/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("PATCH /api/anomalies/{id}/status → 400 for invalid status")
    void updateStatus_invalidStatus_returns400() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));

        mockMvc.perform(patch("/api/anomalies/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INVALID"))))
                .andExpect(status().isBadRequest());
    }
}
