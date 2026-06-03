package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.response.DailyCountResponse;
import talan.pfe.rulengine.dtos.response.GovernanceChartsResponse;
import talan.pfe.rulengine.dtos.response.GovernanceSummaryResponse;
import talan.pfe.rulengine.dtos.response.LabelCountResponse;
import talan.pfe.rulengine.dtos.response.TopRuleSetUsageResponse;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.repositories.AlertConfigRepository;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.NotificationRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.GovernanceService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GovernanceServiceImpl implements GovernanceService {

    private static final int TREND_PERIOD_DAYS = 7;
    private static final int TOP_RULE_SETS_LIMIT = 6;

    private final RuleSetRepository           ruleSetRepository;
    private final EvaluationRequestRepository evaluationRequestRepository;
    private final AlertConfigRepository       alertConfigRepository;
    private final NotificationRepository      notificationRepository;

    @Override
    public GovernanceSummaryResponse getSummary(Long tenantId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime trendFrom    = LocalDate.now().minusDays(TREND_PERIOD_DAYS - 1L).atStartOfDay();
        LocalDateTime topFrom      = startOfDay.minusDays(30);

        long activeRuleSets = ruleSetRepository
                .findAllByTenantWithFilters(tenantId, "", RuleSetStatus.ACTIVE,
                        PageRequest.of(0, 1))
                .getTotalElements();

        Long totalEvaluationsToday = evaluationRequestRepository
                .countByTenantSince(tenantId, startOfDay);

        Double avgMsToday = evaluationRequestRepository
                .avgExecutionMsByTenantSince(tenantId, startOfDay);

        long activeAlerts = alertConfigRepository
                .findAllByTenantIdAndEnabledTrue(tenantId).size();

        long unreadNotifications = notificationRepository
                .countByTenantIdAndReadFalse(tenantId);

        List<TopRuleSetUsageResponse> topRuleSets = mapTopRuleSets(
                evaluationRequestRepository.findTopRuleSetsByTenant(
                        tenantId, topFrom, PageRequest.of(0, TOP_RULE_SETS_LIMIT)));

        if (topRuleSets.isEmpty() && activeRuleSets > 0) {
            topRuleSets = fallbackTopFromActiveRuleSets(tenantId);
        }

        List<Object[]> dailyRows = evaluationRequestRepository
                .findDailyEvaluationsByTenant(tenantId, trendFrom);
        List<Object[]> strategyRows = evaluationRequestRepository
                .findStrategyCountsByTenant(tenantId, trendFrom);

        return GovernanceSummaryResponse.builder()
                .activeRuleSets(activeRuleSets)
                .totalEvaluationsToday(totalEvaluationsToday != null ? totalEvaluationsToday : 0L)
                .avgExecutionMsToday(avgMsToday != null ? avgMsToday : 0.0)
                .activeAlerts(activeAlerts)
                .unreadNotifications(unreadNotifications)
                .topRuleSets(topRuleSets)
                .charts(buildCharts(dailyRows, strategyRows))
                .build();
    }

    private List<TopRuleSetUsageResponse> mapTopRuleSets(List<Object[]> rows) {
        return rows.stream()
                .map(row -> TopRuleSetUsageResponse.builder()
                        .ruleSetId(row[0] != null ? ((Number) row[0]).longValue() : null)
                        .ruleSetName(row[1] != null ? row[1].toString() : null)
                        .evaluationCount(row[2] != null ? ((Number) row[2]).longValue() : 0L)
                        .build())
                .toList();
    }

    private List<TopRuleSetUsageResponse> fallbackTopFromActiveRuleSets(Long tenantId) {
        Page<RuleSet> page = ruleSetRepository.findAllByTenantWithFilters(
                tenantId, "", RuleSetStatus.ACTIVE, PageRequest.of(0, TOP_RULE_SETS_LIMIT));
        return page.getContent().stream()
                .map(rs -> TopRuleSetUsageResponse.builder()
                        .ruleSetId(rs.getId())
                        .ruleSetName(rs.getName())
                        .evaluationCount(0L)
                        .build())
                .toList();
    }

    private GovernanceChartsResponse buildCharts(
            List<Object[]> dailyRows,
            List<Object[]> strategyRows) {

        return GovernanceChartsResponse.builder()
                .evaluationsTrend(buildEvaluationTrend(dailyRows))
                .strategiesByType(buildStrategyDistribution(strategyRows))
                .trendPeriodDays(TREND_PERIOD_DAYS)
                .build();
    }

    private List<DailyCountResponse> buildEvaluationTrend(List<Object[]> rows) {
        Map<LocalDate, Long> countsByDate = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            LocalDate day = LocalDate.parse(row[0].toString());
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            countsByDate.put(day, count);
        }

        LocalDate start = LocalDate.now().minusDays(TREND_PERIOD_DAYS - 1L);
        LocalDate end = LocalDate.now();
        List<DailyCountResponse> trend = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            trend.add(DailyCountResponse.builder()
                    .date(day.toString())
                    .count(countsByDate.getOrDefault(day, 0L))
                    .build());
        }
        return trend;
    }

    private List<LabelCountResponse> buildStrategyDistribution(List<Object[]> rows) {
        Map<EvaluationStrategy, Long> counts = new EnumMap<>(EvaluationStrategy.class);
        for (EvaluationStrategy strategy : EvaluationStrategy.values()) {
            counts.put(strategy, 0L);
        }
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            EvaluationStrategy strategy = (EvaluationStrategy) row[0];
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            counts.put(strategy, count);
        }
        return counts.entrySet().stream()
                .map(e -> LabelCountResponse.builder()
                        .label(e.getKey().name())
                        .count(e.getValue())
                        .build())
                .toList();
    }
}
