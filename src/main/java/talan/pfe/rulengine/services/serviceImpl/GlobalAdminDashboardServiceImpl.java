package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.response.DailyCountResponse;
import talan.pfe.rulengine.dtos.response.GlobalAdminDashboardChartsResponse;
import talan.pfe.rulengine.dtos.response.GlobalAdminDashboardResponse;
import talan.pfe.rulengine.dtos.response.LabelCountResponse;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.GlobalAdminDashboardService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalAdminDashboardServiceImpl implements GlobalAdminDashboardService {

    private static final int MIN_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 90;
    private static final int DEFAULT_TREND_DAYS = 30;

    private final TenantRepository tenantRepository;

    @Override
    public GlobalAdminDashboardResponse getDashboard(int trendPeriodDays) {
        int periodDays = clampTrendDays(trendPeriodDays);

        long activeTenants   = tenantRepository.countByStatus(TenantStatus.ACTIVE);
        long inactiveTenants = tenantRepository.countByStatus(TenantStatus.INACTIVE);

        LocalDateTime from = LocalDate.now().minusDays(periodDays - 1L).atStartOfDay();
        List<Object[]> dailyRows = tenantRepository.findDailyCreationsSince(from);

        return GlobalAdminDashboardResponse.builder()
                .totalTenants(activeTenants + inactiveTenants)
                .activeTenants(activeTenants)
                .inactiveTenants(inactiveTenants)
                .charts(buildCharts(activeTenants, inactiveTenants, dailyRows, periodDays))
                .build();
    }

    private GlobalAdminDashboardChartsResponse buildCharts(
            long activeTenants,
            long inactiveTenants,
            List<Object[]> dailyRows,
            int periodDays) {

        List<LabelCountResponse> tenantsByStatus = List.of(
                LabelCountResponse.builder()
                        .label(TenantStatus.ACTIVE.name())
                        .count(activeTenants)
                        .build(),
                LabelCountResponse.builder()
                        .label(TenantStatus.INACTIVE.name())
                        .count(inactiveTenants)
                        .build()
        );

        return GlobalAdminDashboardChartsResponse.builder()
                .tenantsByStatus(tenantsByStatus)
                .tenantCreationsTrend(buildCreationTrend(dailyRows, periodDays))
                .trendPeriodDays(periodDays)
                .build();
    }

    private List<DailyCountResponse> buildCreationTrend(List<Object[]> rows, int periodDays) {
        Map<LocalDate, Long> countsByDate = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            LocalDate day = LocalDate.parse(row[0].toString());
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            countsByDate.put(day, count);
        }

        LocalDate start = LocalDate.now().minusDays(periodDays - 1L);
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

    private int clampTrendDays(int trendPeriodDays) {
        if (trendPeriodDays < MIN_TREND_DAYS) {
            return DEFAULT_TREND_DAYS;
        }
        return Math.min(trendPeriodDays, MAX_TREND_DAYS);
    }
}
