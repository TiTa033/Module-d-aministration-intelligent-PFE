package talan.pfe.rulengine.dtos.rulesetexport;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportedConditionDto {

    @NotBlank
    private String field;

    @NotBlank
    private String operator;

    @NotBlank
    private String value;

    @NotBlank
    private String valueType;
}
