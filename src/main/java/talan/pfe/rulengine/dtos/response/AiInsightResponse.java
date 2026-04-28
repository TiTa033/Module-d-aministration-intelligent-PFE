package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;
import talan.pfe.rulengine.enums.InsightStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class AiInsightResponse {
    private Long id;
    private Long ruleSetId;
    private String title;
    private String description;
    private InsightStatus status;
    private LocalDateTime generatedAt;
}
