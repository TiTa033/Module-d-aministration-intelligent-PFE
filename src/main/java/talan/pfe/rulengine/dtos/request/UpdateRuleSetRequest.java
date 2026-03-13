package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.*;
import lombok.*;
import talan.pfe.rulengine.enums.EvaluationStrategy;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRuleSetRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    private String description;

    @NotNull(message = "Evaluation strategy is required")
    private EvaluationStrategy evaluationStrategy;
}