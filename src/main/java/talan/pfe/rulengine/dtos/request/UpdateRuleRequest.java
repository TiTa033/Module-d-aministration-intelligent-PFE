package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.*;
import lombok.*;
import talan.pfe.rulengine.enums.LogicOperator;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    private String description;

    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be at least 1")
    private Integer priority;

    @NotNull(message = "Logic operator is required")
    private LogicOperator logicOperator;

    private Integer score;
}