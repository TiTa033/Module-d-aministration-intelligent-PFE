package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;

@Data
public class RuleConditionRequest {

    @NotBlank
    private String field;

    @NotNull
    private Operator operator;

    @NotBlank
    private String value;

    @NotNull
    private DataType valueType;
}

