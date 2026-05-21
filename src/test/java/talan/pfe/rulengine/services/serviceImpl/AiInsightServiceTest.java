package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.InsightStatusUpdateRequest;
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock AiInsightRepository aiInsightRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock RuleSetDocumentationAgent ruleSetDocumentationAgent;

    @InjectMocks AiInsightService service;

    private RuleSet ruleSet(Long id, Long tenantId) {
        return RuleSet.builder().id(id)
                .tenant(Tenant.builder().id(tenantId).build())
                .name("RS-" + id)
                .build();
    }

    private AiInsight pendingInsight(Long id, RuleSet rs) {
        return AiInsight.builder()
                .id(id).ruleSet(rs)
                .tenant(rs.getTenant())
                .title("Doc").description("Desc")
                .type(InsightType.DOCUMENTATION_GENERATED)
                .status(InsightStatus.PENDING)
                .build();
    }

    private User userWithTenant(Long id, Long tenantId) {
        Tenant t = Tenant.builder().id(tenantId).build();
        return User.builder().id(id).email("a@b.com").tenant(t).build();
    }

    // ─── GET RULE SET DOCUMENTATION ──────────────────────────

    @Test
    void getRuleSetDocumentation_returnsListWhenRuleSetExists() {
        RuleSet rs = ruleSet(5L, 10L);
        AiInsight insight = pendingInsight(1L, rs);

        when(ruleSetRepository.findByIdAndTenantId(5L, 10L)).thenReturn(Optional.of(rs));
        when(aiInsightRepository.findByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                5L, 10L, InsightType.DOCUMENTATION_GENERATED))
                .thenReturn(List.of(insight));

        List<AiInsightResponse> result = service.getRuleSetDocumentation(5L, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("Doc");
    }

    @Test
    void getRuleSetDocumentation_throwsWhenRuleSetNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRuleSetDocumentation(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RuleSet not found");
    }

    // ─── GET LATEST RULE SET DOCUMENTATION ───────────────────

    @Test
    void getLatestRuleSetDocumentation_returnsLatestInsight() {
        RuleSet rs = ruleSet(5L, 10L);
        AiInsight insight = pendingInsight(3L, rs);

        when(ruleSetRepository.findByIdAndTenantId(5L, 10L)).thenReturn(Optional.of(rs));
        when(aiInsightRepository.findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                5L, 10L, InsightType.DOCUMENTATION_GENERATED))
                .thenReturn(Optional.of(insight));

        AiInsightResponse result = service.getLatestRuleSetDocumentation(5L, 10L);

        assertThat(result.getId()).isEqualTo(3L);
    }

    @Test
    void getLatestRuleSetDocumentation_throwsWhenNoInsightFound() {
        RuleSet rs = ruleSet(5L, 10L);
        when(ruleSetRepository.findByIdAndTenantId(5L, 10L)).thenReturn(Optional.of(rs));
        when(aiInsightRepository.findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                5L, 10L, InsightType.DOCUMENTATION_GENERATED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestRuleSetDocumentation(5L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Aucune documentation IA trouvée");
    }

    // ─── UPDATE STATUS ────────────────────────────────────────

    @Test
    void updateStatus_acceptsInsightAndSaves() {
        RuleSet rs = ruleSet(5L, 10L);
        AiInsight insight = pendingInsight(1L, rs);
        User user = userWithTenant(1L, 10L);

        InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
        req.setStatus("ACCEPTED");

        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));
        when(aiInsightRepository.save(insight)).thenReturn(insight);

        AiInsightResponse result = service.updateStatus(1L, req);

        assertThat(result.getId()).isEqualTo(1L);
        verify(aiInsightRepository).save(insight);
    }

    @Test
    void updateStatus_rejectsInsightAndSaves() {
        RuleSet rs = ruleSet(5L, 10L);
        AiInsight insight = pendingInsight(1L, rs);
        User user = userWithTenant(1L, 10L);

        InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
        req.setStatus("REJECTED");

        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));
        when(aiInsightRepository.save(insight)).thenReturn(insight);

        AiInsightResponse result = service.updateStatus(1L, req);

        assertThat(result).isNotNull();
        verify(aiInsightRepository).save(insight);
    }

    @Test
    void updateStatus_throwsWhenInsightNotFound() {
        User user = userWithTenant(1L, 10L);
        InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
        req.setStatus("ACCEPTED");

        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(999L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Insight introuvable");
    }

    @Test
    void updateStatus_throwsWhenInsightBelongsToDifferentTenant() {
        RuleSet rs = ruleSet(5L, 99L);
        AiInsight insight = pendingInsight(1L, rs);
        User user = userWithTenant(1L, 10L);

        InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
        req.setStatus("ACCEPTED");

        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));

        assertThatThrownBy(() -> service.updateStatus(1L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Insight introuvable");
    }

    @Test
    void updateStatus_throwsWhenStatusIsPending() {
        RuleSet rs = ruleSet(5L, 10L);
        AiInsight insight = pendingInsight(1L, rs);
        User user = userWithTenant(1L, 10L);

        InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
        req.setStatus("PENDING");

        when(currentUserResolver.requireUser()).thenReturn(user);
        when(aiInsightRepository.findById(1L)).thenReturn(Optional.of(insight));

        assertThatThrownBy(() -> service.updateStatus(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ACCEPTED ou REJECTED");
    }

    @Test
    void updateStatus_throwsWhenNoTenantContext() {
        User globalAdmin = User.builder().id(1L).email("g@x.com").tenant(null).build();
        InsightStatusUpdateRequest req = new InsightStatusUpdateRequest();
        req.setStatus("ACCEPTED");

        when(currentUserResolver.requireUser()).thenReturn(globalAdmin);

        assertThatThrownBy(() -> service.updateStatus(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tenant context");
    }

    // ─── GENERATE DOCUMENTATION ───────────────────────────────

    @Test
    void generateDocumentationNow_callsAgentWhenRuleSetExists() {
        RuleSet rs = ruleSet(5L, 10L);
        when(ruleSetRepository.findByIdAndTenantId(5L, 10L)).thenReturn(Optional.of(rs));
        doNothing().when(ruleSetDocumentationAgent).generateDocumentation(5L, 10L);

        service.generateDocumentationNow(5L, 10L);

        verify(ruleSetDocumentationAgent).generateDocumentation(5L, 10L);
    }

    @Test
    void generateDocumentationNow_throwsWhenRuleSetNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateDocumentationNow(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}