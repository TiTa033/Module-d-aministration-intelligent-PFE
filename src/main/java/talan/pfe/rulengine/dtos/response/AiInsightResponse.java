package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;

import java.time.LocalDateTime;

@Data
@Builder
public class AiInsightResponse {
    private Long id;
    private Long ruleSetId;
    private String title;
    private String description;
    private String suggestion;
    private InsightStatus status;
    private InsightType type;
    private AgentType agentType;
    private Float confidence;
    private LocalDateTime generatedAt;
    private LocalDateTime resolvedAt;
}
