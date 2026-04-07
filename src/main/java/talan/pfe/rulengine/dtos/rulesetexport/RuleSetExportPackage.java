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
public class RuleSetExportPackage {

    public static final int CURRENT_SCHEMA = 1;

    @NotNull
    @Builder.Default
    private Integer schemaVersion = CURRENT_SCHEMA;

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String evaluationStrategy;

    /** DRAFT, ACTIVE, ARCHIVED — optional on older exports. */
    private String status;

    @Valid
    @Builder.Default
    private List<ExportedRuleDto> rules = new ArrayList<>();
}
