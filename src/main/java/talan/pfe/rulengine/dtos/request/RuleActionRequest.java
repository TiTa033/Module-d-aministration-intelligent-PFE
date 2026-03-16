package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import talan.pfe.rulengine.enums.ActionType;

@Data
public class RuleActionRequest {

    @NotNull
    private ActionType actionType;

    @NotBlank
    private String outputKey;

    @NotBlank
    private String outputValue;
}

