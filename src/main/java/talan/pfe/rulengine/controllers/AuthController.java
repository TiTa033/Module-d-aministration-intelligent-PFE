package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.AuthResponse;
import talan.pfe.rulengine.services.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication",
        description = "Login, Register, Refresh token and Logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login → returns access + refresh tokens")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP code → returns access + refresh tokens")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user → returns access + refresh tokens")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — revokes all refresh tokens for the user")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP to email")
    public ResponseEntity<Void> resendOtp(@RequestBody Map<String, String> body) {
        authService.resendOtp(body.get("email"));
        return ResponseEntity.ok().build();
    }
}
