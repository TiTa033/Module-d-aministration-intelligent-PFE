package talan.pfe.rulengine.dtos.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleResponse {

    private Long id;
    private String name;
    private String description;
    private Integer priority;
    private boolean enabled;
    private String logicOperator;
    private Integer score;
    private Long ruleSetId;
    private String ruleSetName;
    private int totalConditions;
    private int totalActions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


}