package talan.pfe.rulengine.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopRuleSetUsageResponse {
    private Long   ruleSetId;
    private String ruleSetName;
    private Long   evaluationCount;
}
