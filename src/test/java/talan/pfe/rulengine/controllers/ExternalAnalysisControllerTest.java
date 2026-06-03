package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExternalAnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ExternalAnalysisController")
class ExternalAnalysisControllerTest {

    @Autowired MockMvc mockMvc;

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
                .title("External Analysis Result")
                .description("External market data analyzed")
                .type(InsightType.DOCUMENTATION_GENERATED)
                .agentType(AgentType.EXTERNAL_ANALYSIS_AGENT)
                .status(InsightStatus.PENDING)
                .confidence(0.85f)
                .tenant(tenant)
                .build();
    }

    @Test @DisplayName("GET /api/external-analysis/insights → 200 OK")
    void getInsights_returns200() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findByTenantIdAndAgentTypeOrderByGeneratedAtDesc(
                1L, AgentType.EXTERNAL_ANALYSIS_AGENT))
                .thenReturn(List.of(insight));

        mockMvc.perform(get("/api/external-analysis/insights"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/external-analysis/insights → 400 when user has no tenant")
    void getInsights_noTenant_returns400() throws Exception {
        User noTenantUser = User.builder().id(3L).email("global@raas.com")
                .role(Role.GLOBAL_ADMIN).tenant(null).build();
        when(currentUserResolver.requireUser()).thenReturn(noTenantUser);

        mockMvc.perform(get("/api/external-analysis/insights"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("GET /api/external-analysis/insights → 200 OK with empty list")
    void getInsights_emptyList_returns200() throws Exception {
        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findByTenantIdAndAgentTypeOrderByGeneratedAtDesc(
                1L, AgentType.EXTERNAL_ANALYSIS_AGENT))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/external-analysis/insights"))
                .andExpect(status().isOk());
    }
}
