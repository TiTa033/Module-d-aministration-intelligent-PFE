package talan.pfe.rulengine.dtos.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import talan.pfe.rulengine.enums.EvaluationStrategy;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateResponse {

    private Long evaluationRequestId;
    private Long ruleSetId;
    private String ruleSetName;
    private EvaluationStrategy strategyUsed;
    private JsonNode output;
    private JsonNode matchedRules;
    private Double totalScore;
    private long executionTimeMs;
}
