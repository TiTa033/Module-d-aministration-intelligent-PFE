package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;
import talan.pfe.rulengine.entites.RuleCondition;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;


@Data
@Builder
public class RuleConditionResponse {

    private Long id;
    private String field;
    private Operator operator;
    private String value;
    private DataType valueType;
}

