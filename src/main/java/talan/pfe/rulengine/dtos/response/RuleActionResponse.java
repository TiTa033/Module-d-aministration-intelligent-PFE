package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;
import talan.pfe.rulengine.entites.RuleAction;
import talan.pfe.rulengine.enums.ActionType;


@Data
@Builder
public class RuleActionResponse {

    private Long id;
    private ActionType actionType;
    private String outputKey;
    private String outputValue;
}

