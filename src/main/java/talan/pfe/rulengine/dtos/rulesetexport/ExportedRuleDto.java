package talan.pfe.rulengine.dtos.rulesetexport;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportedRuleDto {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Integer priority;

    @NotNull
    private Boolean enabled;

    @NotBlank
    private String logicOperator;

    private Integer score;

    @Valid
    @Builder.Default
    private List<ExportedConditionDto> conditions = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<ExportedActionDto> actions = new ArrayList<>();
}
