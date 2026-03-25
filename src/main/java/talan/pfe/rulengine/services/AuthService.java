package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.ForgotPasswordRequest;
import talan.pfe.rulengine.dtos.request.LoginRequest;
import talan.pfe.rulengine.dtos.request.RefreshTokenRequest;
import talan.pfe.rulengine.dtos.request.RegisterRequest;
import talan.pfe.rulengine.dtos.request.ResetPasswordRequest;
import talan.pfe.rulengine.dtos.request.VerifyOtpRequest;
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
