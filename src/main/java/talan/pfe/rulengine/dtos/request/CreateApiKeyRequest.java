package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotNull(message = "RuleSet is required")
    private Long ruleSetId;

    // Optional expiry date — null means never expires
    private java.time.LocalDateTime expiresAt;
}