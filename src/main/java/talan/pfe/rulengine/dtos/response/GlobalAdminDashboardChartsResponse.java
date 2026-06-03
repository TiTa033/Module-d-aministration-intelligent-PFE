package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GlobalAdminDashboardChartsResponse {

    /** Répartition actifs / inactifs (camembert, barres). */
    private List<LabelCountResponse> tenantsByStatus;

    /** Nouveaux tenants par jour sur la période (courbe, histogramme). */
    private List<DailyCountResponse> tenantCreationsTrend;

    private int trendPeriodDays;
}
