package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.response.EvaluationDetailResponse;
import talan.pfe.rulengine.dtos.response.EvaluationHistoryResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.entites.EvaluationRequest;
import talan.pfe.rulengine.entites.EvaluationResult;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.services.EvaluationHistoryService;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import talan.pfe.rulengine.repositories.EvaluationRequestSpecification;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationHistoryServiceImpl implements EvaluationHistoryService {

    private final EvaluationRequestRepository evaluationRequestRepository;
    private final ObjectMapper objectMapper;

    @Override
    public PageResponse<EvaluationHistoryResponse> getAll(
            Long tenantId,
            Long ruleSetId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "requestedAt")
        );

        Page<EvaluationHistoryResponse> page = evaluationRequestRepository
                .findAll(
                        EvaluationRequestSpecification.withFilters(tenantId, ruleSetId, from, to),
                        sortedPageable)
                .map(this::toSummary);

        return PageResponse.from(page);
    }

    @Override
    public EvaluationDetailResponse getById(Long id, Long tenantId) {
        EvaluationRequest req = evaluationRequestRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Evaluation not found with id: " + id));
        return toDetail(req);
    }

    // ─── MAPPERS ────────────────────────────────────────────

    private EvaluationHistoryResponse toSummary(EvaluationRequest req) {
        EvaluationResult res = req.getResult();
        int matchedCount = 0;
        if (res != null && res.getMatchedRules() != null) {
            try {
                JsonNode node = objectMapper.readTree(res.getMatchedRules());
                if (node.isArray()) matchedCount = node.size();
            } catch (Exception ignored) {}
        }

        return EvaluationHistoryResponse.builder()
                .id(req.getId())
                .requestedAt(req.getRequestedAt())
                .evaluatedAt(res != null ? res.getEvaluatedAt() : null)
                .ruleSetId(req.getRuleSet() != null ? req.getRuleSet().getId() : null)
                .ruleSetName(req.getRuleSet() != null ? req.getRuleSet().getName() : null)
                .apiKeyName(req.getApiKey() != null ? req.getApiKey().getName() : null)
                .apiKeyPrefix(req.getApiKey() != null ? req.getApiKey().getKeyPrefix() : null)
                .strategyUsed(res != null ? res.getStrategyUsed() : null)
                .totalScore(res != null ? res.getTotalScore() : null)
                .executionTimeMs(res != null ? res.getExecutionTimeMs() : null)
                .matchedRulesCount(matchedCount)
                .tenantId(req.getTenant() != null ? req.getTenant().getId() : null)
                .tenantName(req.getTenant() != null ? req.getTenant().getName() : null)
                .build();
    }

    private EvaluationDetailResponse toDetail(EvaluationRequest req) {
        EvaluationResult res = req.getResult();

        return EvaluationDetailResponse.builder()
                .id(req.getId())
                .requestedAt(req.getRequestedAt())
                .evaluatedAt(res != null ? res.getEvaluatedAt() : null)
                .ruleSetId(req.getRuleSet() != null ? req.getRuleSet().getId() : null)
                .ruleSetName(req.getRuleSet() != null ? req.getRuleSet().getName() : null)
                .apiKeyName(req.getApiKey() != null ? req.getApiKey().getName() : null)
                .apiKeyPrefix(req.getApiKey() != null ? req.getApiKey().getKeyPrefix() : null)
                .strategyUsed(res != null ? res.getStrategyUsed() : null)
                .totalScore(res != null ? res.getTotalScore() : null)
                .executionTimeMs(res != null ? res.getExecutionTimeMs() : null)
                .tenantId(req.getTenant() != null ? req.getTenant().getId() : null)
                .tenantName(req.getTenant() != null ? req.getTenant().getName() : null)
                .inputPayload(req.getInputPayload())
                .outputPayload(res != null ? res.getOutputPayload() : null)
                .matchedRules(res != null ? res.getMatchedRules() : null)
                .build();
    }
}