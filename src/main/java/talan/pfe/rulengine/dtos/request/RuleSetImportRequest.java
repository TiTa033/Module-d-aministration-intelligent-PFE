package talan.pfe.rulengine.dtos.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetImportRequest {

    @NotNull
    private JsonNode packageJson;

    /**
     * When true, import fails if a RuleSet with the same name already exists.
     */
    @Builder.Default
    private boolean failIfNameExists = true;

    /**
     * Optional new name (overrides package name when set).
     */
    private String targetName;
}
