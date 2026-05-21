package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.AuthResponse;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.AuthService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private AuthResponse tokenResponse() {
        return AuthResponse.builder()
                .accessToken("at").refreshToken("rt")
                .email("a@b.com").role("ADMIN").tenantId("5")
                .accessTokenExpiresIn(900000L).refreshTokenExpiresIn(604800000L)
                .build();
    }

    // ─── LOGIN ───────────────────────────────────────────────

    @Test
    void login_returns200WithOtpFlag() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .email("a@b.com").requiresOtp(true).build();
        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "a@b.com", "password", "pass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true))
                .andExpect(jsonPath("$.email").value("a@b.com"));
    }

    // ─── VERIFY OTP ──────────────────────────────────────────

    @Test
    void verifyOtp_returns200WithTokens() throws Exception {
        when(authService.verifyOtp(any())).thenReturn(tokenResponse());

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "a@b.com", "otpCode", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("at"))
                .andExpect(jsonPath("$.refreshToken").value("rt"));
    }

    @Test
    void verifyOtp_returns404WhenUserNotFound() throws Exception {
        when(authService.verifyOtp(any()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ghost@b.com", "otpCode", "000"))))
                .andExpect(status().isNotFound());
    }

    // ─── REGISTER ────────────────────────────────────────────

    @Test
    void register_returns201WithTokens() throws Exception {
        when(authService.register(any())).thenReturn(tokenResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Alice", "lastName", "Smith",
                                "email", "a@b.com", "password", "secure123",
                                "tenantId", "5"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ─── REFRESH ─────────────────────────────────────────────

    @Test
    void refresh_returns200WithNewTokens() throws Exception {
        when(authService.refresh(any())).thenReturn(tokenResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "old-rt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("at"));
    }

    @Test
    void refresh_returns401WhenTokenInvalid() throws Exception {
        when(authService.refresh(any()))
                .thenThrow(new TokenException("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "bad"))))
                .andExpect(status().isUnauthorized());
    }

    // ─── LOGOUT ──────────────────────────────────────────────

    @Test
    void logout_returns204() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "rt"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_returns401WhenTokenNotFound() throws Exception {
        doThrow(new TokenException("Refresh token not found"))
                .when(authService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "bad"))))
                .andExpect(status().isUnauthorized());
    }

    // ─── FORGOT PASSWORD ─────────────────────────────────────

    @Test
    void forgotPassword_returns204() throws Exception {
        doNothing().when(authService).forgotPassword(any(), anyString());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reset-Base-Url", "http://front/reset")
                        .content(objectMapper.writeValueAsString(Map.of("email", "a@b.com"))))
                .andExpect(status().isNoContent());
    }

    // ─── RESEND OTP ──────────────────────────────────────────

    @Test
    void resendOtp_returns200() throws Exception {
        doNothing().when(authService).resendOtp("a@b.com");

        mockMvc.perform(post("/api/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "a@b.com"))))
                .andExpect(status().isOk());
    }

    @Test
    void resendOtp_returns404WhenUserNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("User not found"))
                .when(authService).resendOtp("ghost@x.com");

        mockMvc.perform(post("/api/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ghost@x.com"))))
                .andExpect(status().isNotFound());
    }

    // ─── CONSUME RESET LINK ───────────────────────────────────

    @Test
    void consumeResetLink_returns303WithCookie() throws Exception {
        mockMvc.perform(get("/api/auth/reset-password-link").param("token", "abc123"))
                .andExpect(status().isSeeOther())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Location",
                        "http://localhost:4200/authentication/reset-password"));
    }

    // ─── RESET PASSWORD ───────────────────────────────────────

    @Test
    void resetPassword_returns204() throws Exception {
        doNothing().when(authService).resetPassword(anyString(), any());

        mockMvc.perform(post("/api/auth/reset-password")
                        .cookie(new jakarta.servlet.http.Cookie("reset_token", "tok"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "newPass1!"))))
                .andExpect(status().isNoContent());
    }
}