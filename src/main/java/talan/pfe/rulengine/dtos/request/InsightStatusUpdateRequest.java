package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InsightStatusUpdateRequest {
    @NotBlank
    private String status;
}
