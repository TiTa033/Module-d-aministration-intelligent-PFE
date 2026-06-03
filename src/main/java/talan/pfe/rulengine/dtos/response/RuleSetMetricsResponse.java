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
public class RuleSetMetricsResponse {
    private Long              ruleSetId;
    private String            ruleSetName;
    private int               periodDays;
    private Long              totalEvaluations;
    private Double            avgExecutionMs;
    private List<DailyStatResponse> dailyStats;
}
