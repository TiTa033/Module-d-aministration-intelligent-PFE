package talan.pfe.rulengine.dtos.request;


import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Slug is required")
    @Size(min = 2, max = 50, message = "Slug must be between 2 and 50 characters")
    @Pattern(
            regexp = "^[a-z0-9-]+$",
            message = "Slug must contain only lowercase letters, numbers and hyphens"
    )
    private String slug;

    private String description;
}