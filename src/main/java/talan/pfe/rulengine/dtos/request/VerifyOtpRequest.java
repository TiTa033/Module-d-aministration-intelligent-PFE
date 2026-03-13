package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "OTP code is required")
    private String otpCode;
}