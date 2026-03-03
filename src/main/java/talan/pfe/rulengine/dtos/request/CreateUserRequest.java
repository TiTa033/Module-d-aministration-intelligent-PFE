package talan.pfe.rulengine.dtos.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import talan.pfe.rulengine.enums.Role;

@Data
public class CreateUserRequest {
    @Email @NotBlank private String email;
    @NotBlank private String password;
    @NotNull private Role role;
}
