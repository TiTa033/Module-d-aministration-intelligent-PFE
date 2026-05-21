package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.enums.InsightStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class AnomalyAlertResponse {
    private Long id;
    private String title;
    private String description;
    private String contextData;
    private String suggestion;
    private InsightStatus status;
    private Float confidence;
    private LocalDateTime generatedAt;

    public static AnomalyAlertResponse from(AiInsight insight) {
        return AnomalyAlertResponse.builder()
                .id(insight.getId())
                .title(insight.getTitle())
                .description(insight.getDescription())
                .contextData(insight.getContextData())
                .suggestion(insight.getSuggestion())
                .status(insight.getStatus())
                .confidence(insight.getConfidence())
                .generatedAt(insight.getGeneratedAt())
                .build();
    }
}
