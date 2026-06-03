package talan.pfe.rulengine.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import talan.pfe.rulengine.enums.AlertCondition;
import talan.pfe.rulengine.enums.AlertMetric;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertConfigResponse {
    private Long           id;
    private String         name;
    private AlertMetric    metric;
    private AlertCondition conditionType;
    private Double         threshold;
    private Integer        windowHours;
    private boolean        enabled;
    private Long           ruleSetId;
    private String         ruleSetName;
    private LocalDateTime  createdAt;
}
