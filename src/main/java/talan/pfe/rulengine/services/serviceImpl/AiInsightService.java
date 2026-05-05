package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.InsightStatusUpdateRequest;
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiInsightService {

    private final AiInsightRepository aiInsightRepository;
    private final RuleSetRepository ruleSetRepository;
    private final CurrentUserResolver currentUserResolver;
    private final RuleSetDocumentationAgent ruleSetDocumentationAgent;

    @Transactional(readOnly = true)
    public List<AiInsightResponse> getRuleSetDocumentation(Long ruleSetId, Long tenantId) {
        ensureRuleSet(ruleSetId, tenantId);
        return aiInsightRepository.findByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                        ruleSetId, tenantId, InsightType.DOCUMENTATION_GENERATED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiInsightResponse getLatestRuleSetDocumentation(Long ruleSetId, Long tenantId) {
        ensureRuleSet(ruleSetId, tenantId);
        AiInsight insight = aiInsightRepository.findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
                        ruleSetId, tenantId, InsightType.DOCUMENTATION_GENERATED)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune documentation IA trouvée."));
        return toResponse(insight);
    }

    @Transactional
    public AiInsightResponse updateStatus(Long insightId, InsightStatusUpdateRequest request) {
        User currentUser = currentUserResolver.requireUser();
        Long tenantId = currentUser.getTenant() != null ? currentUser.getTenant().getId() : null;
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required.");
        }

        AiInsight insight = aiInsightRepository.findById(insightId)
                .orElseThrow(() -> new ResourceNotFoundException("Insight introuvable."));
        if (!insight.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("Insight introuvable.");
        }

        InsightStatus next = parseStatus(request.getStatus());
        if (next == InsightStatus.ACCEPTED) {
            insight.accept(currentUser);
        } else if (next == InsightStatus.REJECTED) {
            insight.reject(currentUser);
        } else {
            throw new BadRequestException("Seuls ACCEPTED ou REJECTED sont autorisés.");
        }

        return toResponse(aiInsightRepository.save(insight));
    }

    @Transactional
    public void generateDocumentationNow(Long ruleSetId, Long tenantId) {
        ensureRuleSet(ruleSetId, tenantId);
        ruleSetDocumentationAgent.generateDocumentation(ruleSetId, tenantId);
    }

    private void ensureRuleSet(Long ruleSetId, Long tenantId) {
        ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet not found with id: " + ruleSetId));
    }

    private InsightStatus parseStatus(String raw) {
        try {
            return InsightStatus.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid status. Use ACCEPTED or REJECTED.");
        }
    }

    private AiInsightResponse toResponse(AiInsight entity) {
        return AiInsightResponse.builder()
                .id(entity.getId())
                .ruleSetId(entity.getRuleSet() != null ? entity.getRuleSet().getId() : null)
                .title(entity.getTitle())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
