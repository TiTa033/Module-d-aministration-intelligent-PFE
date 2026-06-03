package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GovernanceChartsResponse {

    private List<DailyCountResponse>   evaluationsTrend;
    private List<LabelCountResponse>   strategiesByType;
    private int trendPeriodDays;
}
