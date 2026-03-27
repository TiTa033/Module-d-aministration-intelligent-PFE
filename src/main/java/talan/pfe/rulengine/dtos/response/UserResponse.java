package talan.pfe.rulengine.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import talan.pfe.rulengine.enums.Role;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private Role role;
    private Long tenantId;
    private String tenantName;
    private boolean active;
}
