package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.AuthResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.AuthService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private AuthResponse stubAuth() {
        return AuthResponse.builder()
                .accessToken("access.token").refreshToken("refresh.token")
                .email("user@bank.com").role("ADMIN").tenantId("1")
                .requiresOtp(false).build();
    }

    @Test @DisplayName("POST /api/auth/login → 200 OK")
    void login_returns200() throws Exception {
        when(authService.login(any())).thenReturn(stubAuth());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@bank.com", "password", "secret", "captchaToken", "tok"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /api/auth/verify-otp → 200 OK")
    void verifyOtp_returns200() throws Exception {
        when(authService.verifyOtp(any())).thenReturn(stubAuth());

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@bank.com", "otpCode", "123456"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /api/auth/register → 201 CREATED")
    void register_returns201() throws Exception {
        when(authService.register(any())).thenReturn(stubAuth());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "new@bank.com", "password", "secret123",
                                        "tenantId", "1", "role", "VIEWER"))))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("POST /api/auth/refresh → 200 OK")
    void refresh_returns200() throws Exception {
        when(authService.refresh(any())).thenReturn(stubAuth());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", "refresh.token"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /api/auth/logout → 204 NO CONTENT")
    void logout_returns204() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", "refresh.token"))))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("POST /api/auth/resend-otp → 200 OK")
    void resendOtp_returns200() throws Exception {
        doNothing().when(authService).resendOtp(any());

        mockMvc.perform(post("/api/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@bank.com"))))
                .andExpect(status().isOk());
    }
}
