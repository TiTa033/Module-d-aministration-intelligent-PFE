package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.AuthResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    void forgotPassword(ForgotPasswordRequest request, String resetBaseUrl);

    void resetPassword(String tokenValue, ResetPasswordRequest request);

    void resendOtp(String email);
}