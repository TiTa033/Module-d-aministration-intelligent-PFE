package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;
import talan.pfe.rulengine.entites.RuleAction;
import talan.pfe.rulengine.enums.ActionType;

import java.util.UUID;

@Data
@Builder
public class RuleActionResponse {

    private UUID id;
    private ActionType actionType;
    private String outputKey;
    private String outputValue;

    public static RuleActionResponse from(RuleAction action) {
        return RuleActionResponse.builder()
                .id(action.getId())
                .actionType(action.getActionType())
                .outputKey(action.getOutputKey())
                .outputValue(action.getOutputValue())
                .build();
    }
}

