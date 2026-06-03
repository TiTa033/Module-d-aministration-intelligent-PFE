package talan.pfe.rulengine.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernanceSummaryResponse {
    private long   activeRuleSets;
    private long   totalEvaluationsToday;
    private double avgExecutionMsToday;
    private long   activeAlerts;
    private long   unreadNotifications;
    private List<TopRuleSetUsageResponse> topRuleSets;
    private GovernanceChartsResponse charts;
}
