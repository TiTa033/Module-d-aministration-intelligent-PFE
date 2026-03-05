package talan.pfe.rulengine.dtos.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String email;
    private String role;
    private String tenantId;
    private long accessTokenExpiresIn;
    private long refreshTokenExpiresIn;
}