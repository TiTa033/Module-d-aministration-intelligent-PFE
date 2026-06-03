package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.response.DailyStatResponse;
import talan.pfe.rulengine.dtos.response.RuleSetMetricsResponse;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.MetricsService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsServiceImpl implements MetricsService {

    private final EvaluationRequestRepository evaluationRequestRepository;
    private final RuleSetRepository           ruleSetRepository;

    @Override
    public RuleSetMetricsResponse getRuleSetMetrics(Long ruleSetId, Long tenantId, int periodDays) {

        RuleSet ruleSet = ruleSetRepository
                .findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));

        LocalDateTime from = LocalDateTime.now().minusDays(periodDays);

        Long   total  = evaluationRequestRepository
                .countByRuleSetAndTenantSince(ruleSetId, tenantId, from);
        Double avgMs  = evaluationRequestRepository
                .avgExecutionMsByRuleSetSince(ruleSetId, tenantId, from);

        List<Object[]> rows = evaluationRequestRepository
                .findDailyStatsByRuleSet(ruleSetId, tenantId, from);

        List<DailyStatResponse> dailyStats = rows.stream()
                .map(row -> DailyStatResponse.builder()
                        .date(row[0] != null ? row[0].toString() : null)
                        .count(row[1] != null ? ((Number) row[1]).longValue() : 0L)
                        .avgExecutionMs(row[2] != null ? ((Number) row[2]).doubleValue() : 0.0)
                        .build())
                .toList();

        return RuleSetMetricsResponse.builder()
                .ruleSetId(ruleSetId)
                .ruleSetName(ruleSet.getName())
                .periodDays(periodDays)
                .totalEvaluations(total != null ? total : 0L)
                .avgExecutionMs(avgMs != null ? avgMs : 0.0)
                .dailyStats(dailyStats)
                .build();
    }
}
