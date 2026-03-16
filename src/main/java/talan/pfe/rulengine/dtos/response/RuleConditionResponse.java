package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;
import talan.pfe.rulengine.entites.RuleCondition;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;

import java.util.UUID;

@Data
@Builder
public class RuleConditionResponse {

    private UUID id;
    private String field;
    private Operator operator;
    private String value;
    private DataType valueType;

    public static RuleConditionResponse from(RuleCondition condition) {
        return RuleConditionResponse.builder()
                .id(condition.getId())
                .field(condition.getField())
                .operator(condition.getOperator())
                .value(condition.getValue())
                .valueType(condition.getValueType())
                .build();
    }
}

