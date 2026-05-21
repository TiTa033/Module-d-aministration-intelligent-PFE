package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.RuleSetVersionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentationPdfServiceTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock AiInsightRepository aiInsightRepository;
    @Mock RuleSetVersionRepository ruleSetVersionRepository;
    @Mock PlatformGuideAgent platformGuideAgent;

    @InjectMocks DocumentationPdfService pdfService;

    private RuleSet ruleSet(Long id) {
        return RuleSet.builder()
                .id(id).name("CreditRS")
                .status(RuleSetStatus.ACTIVE)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .tenant(Tenant.builder().id(10L).name("Acme").build())
                .currentVersion(1)
                .build();
    }

    private AiInsight insight() {
        return AiInsight.builder()
                .id(1L)
                .type(InsightType.DOCUMENTATION_GENERATED)
                .agentType(AgentType.DOCUMENTATION_AGENT)
                .status(InsightStatus.PENDING)
                .title("Documentation CreditRS v1")
                .description("This RuleSet evaluates credit applications.")
                .confidence(0.87f)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ─── buildRuleSetDocumentationPdf ─────────────────────────

    @Test
    void buildRuleSetDocumentationPdf_whenRuleSetNotFound_throwsResourceNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdfService.buildRuleSetDocumentationPdf(99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void buildRuleSetDocumentationPdf_whenInsightNotFound_throwsResourceNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(ruleSet(1L)));
        when(aiInsightRepository.findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                eq(1L), eq(10L), eq(InsightType.DOCUMENTATION_GENERATED)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdfService.buildRuleSetDocumentationPdf(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("documentation");
    }

    @Test
    void buildRuleSetDocumentationPdf_returnsNonNullBytes() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(ruleSet(1L)));
        when(aiInsightRepository.findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                eq(1L), eq(10L), eq(InsightType.DOCUMENTATION_GENERATED)))
                .thenReturn(Optional.of(insight()));
        when(ruleSetVersionRepository.findByRuleSetIdOrderByVersionNumberDesc(1L))
                .thenReturn(List.of());

        byte[] pdf = pdfService.buildRuleSetDocumentationPdf(1L, 10L);

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    // ─── buildEngineGuidePdf ──────────────────────────────────

    @Test
    void buildEngineGuidePdf_callsPlatformGuideAndReturnsBytes() {
        when(platformGuideAgent.generateGuide()).thenReturn("# Guide du moteur\n\nSection 1...");

        byte[] pdf = pdfService.buildEngineGuidePdf();

        assertThat(pdf).isNotNull().isNotEmpty();
        verify(platformGuideAgent).generateGuide();
    }

    @Test
    void buildEngineGuidePdf_withLongContent_returnsNonEmptyBytes() {
        String longContent = "Contenu ".repeat(500);
        when(platformGuideAgent.generateGuide()).thenReturn(longContent);

        byte[] pdf = pdfService.buildEngineGuidePdf();

        assertThat(pdf).isNotEmpty();
    }
}