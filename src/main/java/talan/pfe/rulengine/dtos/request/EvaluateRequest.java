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
public class EvaluateRequest {

    /**
     * JSON object used as evaluation input (nested maps supported; fields use dot paths).
     */
    @NotNull
    private JsonNode input;
}
