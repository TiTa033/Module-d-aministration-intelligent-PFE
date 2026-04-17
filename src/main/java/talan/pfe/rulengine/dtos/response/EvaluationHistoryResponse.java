package talan.pfe.rulengine.dtos.response;

import lombok.*;
import talan.pfe.rulengine.enums.EvaluationStrategy;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationHistoryResponse {

    private Long id;
    private LocalDateTime requestedAt;
    private LocalDateTime evaluatedAt;
    private Long ruleSetId;
    private String ruleSetName;
    private String apiKeyName;
    private String apiKeyPrefix;
    private EvaluationStrategy strategyUsed;
    private Double totalScore;
    private Long executionTimeMs;
    private int matchedRulesCount;
    private Long tenantId;
    private String tenantName;
}