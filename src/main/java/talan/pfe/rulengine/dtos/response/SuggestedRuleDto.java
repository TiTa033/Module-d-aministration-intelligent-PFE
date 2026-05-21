package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SuggestedRuleDto {
    private String name;
    private String description;
    private Integer priority;
    private String logicOperator;
    private Integer score;
    private List<SuggestedConditionDto> conditions;
    private List<SuggestedActionDto> actions;
    private String reasoning;
    private Double confidence;
    private Boolean alreadyExists;
    private String duplicateReason;
}
