package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.InsightStatusUpdateRequest;
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.*;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiInsightService")
class AiInsightServiceTest {

    @Mock AiInsightRepository aiInsightRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock RuleSetDocumentationAgent ruleSetDocumentationAgent;

    @InjectMocks AiInsightService service;

    private Tenant tenant;
    private User user;
    private RuleSet ruleSet;
    private AiInsight insight;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        user = User.builder().id(2L).email("admin@bank.com").role(Role.ADMIN).tenant(tenant).active(true).build();
        ruleSet = RuleSet.builder().id(10L).name("Credit RS")
                .status(RuleSetStatus.ACTIVE).tenant(tenant)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .rules(new ArrayList<>()).build();
        insight = AiInsight.builder()
                .id(1L).type(InsightType.DOCUMENTATION_GENERATED)
                .agentType(AgentType.DOCUMENTATION_AGENT)
                .title("Doc IA - Credit RS").description("Ce RuleSet évalue...")
                .status(InsightStatus.PENDING).confidence(0.87f)
                .tenant(tenant).ruleSet(ruleSet).build();
    }

    @Nested @DisplayName("getRuleSetDocumentation()")
    class GetDocumentation {

        @Test @DisplayName("should return list of documentation insights")
        void getDocumentation_returnsList() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(aiInsightRepository.findByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                    10L, 1L, InsightType.DOCUMENTATION_GENERATED))
                    .thenReturn(List.of(insight));

            List<AiInsightResponse> result = service.getRuleSetDocumentation(10L, 1L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("Doc IA - Credit RS");
        }

        @Test @DisplayName("should throw ResourceNotFoundException when ruleset not found")
        void getDocumentation_ruleSetNotFound() {
            when(ruleSetRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getRuleSetDocumentation(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("getLatestRuleSetDocumentation()")
    class GetLatestDocumentation {

        @Test @DisplayName("should return latest insight")
        void getLatest_found() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(aiInsightRepository.findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                    10L, 1L, InsightType.DOCUMENTATION_GENERATED))
                    .thenReturn(Optional.of(insight));

            AiInsightResponse result = service.getLatestRuleSetDocumentation(10L, 1L);
            assertThat(result.getDescription()).isEqualTo("Ce RuleSet évalue...");
        }

        @Test @DisplayName("should throw ResourceNotFoundException when no documentation exists")
        void getLatest_notFound() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(aiInsightRepository.findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                    10L, 1L, InsightType.DOCUMENTATION_GENERATED))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLatestRuleSetDocumentation(10L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("documentation");
        }
    }

    @Nested @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test @DisplayName("should accept a PENDING insight")
        void updateStatus_accept() {
            when(currentUserResolver.requireUser()).thenReturn(user);
            when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));
            when(aiInsightRepository.save(any())).thenReturn(insight);

            InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
            req.setStatus("ACCEPTED");

            AiInsightResponse result = service.updateStatus(1L, req);
            assertThat(insight.getStatus()).isEqualTo(InsightStatus.ACCEPTED);
            assertThat(result).isNotNull();
        }

        @Test @DisplayName("should reject a PENDING insight")
        void updateStatus_reject() {
            when(currentUserResolver.requireUser()).thenReturn(user);
            when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));
            when(aiInsightRepository.save(any())).thenReturn(insight);

            InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
            req.setStatus("REJECTED");

            service.updateStatus(1L, req);
            assertThat(insight.getStatus()).isEqualTo(InsightStatus.REJECTED);
        }

        @Test @DisplayName("should throw BadRequestException for invalid status value")
        void updateStatus_invalidStatus_throwsBadRequest() {
            when(currentUserResolver.requireUser()).thenReturn(user);
            when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));

            InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
            req.setStatus("INVALID_STATUS");

            assertThatThrownBy(() -> service.updateStatus(1L, req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test @DisplayName("should throw ResourceNotFoundException when insight not found")
        void updateStatus_insightNotFound() {
            when(currentUserResolver.requireUser()).thenReturn(user);
            when(aiInsightRepository.findById(99L)).thenReturn(Optional.empty());

            InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
            req.setStatus("ACCEPTED");

            assertThatThrownBy(() -> service.updateStatus(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("should throw BadRequestException when user has no tenant")
        void updateStatus_noTenant_throwsBadRequest() {
            User noTenantUser = User.builder().id(3L).email("global@raas.com")
                    .role(Role.GLOBAL_ADMIN).tenant(null).build();
            when(currentUserResolver.requireUser()).thenReturn(noTenantUser);

            InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
            req.setStatus("ACCEPTED");

            assertThatThrownBy(() -> service.updateStatus(1L, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tenant context");
        }
    }
}
