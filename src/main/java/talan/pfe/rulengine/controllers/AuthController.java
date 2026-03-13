package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
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


    @PostMapping("/forgot-password")
    @Operation(summary = "Send password reset email")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            @RequestHeader(value = "X-Reset-Base-Url") String resetBaseUrl) {
        authService.forgotPassword(request, resetBaseUrl);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reset-password-link")
    @Operation(summary = "Consume reset token and set cookie, then redirect to frontend page")
    public ResponseEntity<Void> consumeResetLink(@RequestParam("token") String token) {
        ResponseCookie cookie = ResponseCookie.from("reset_token", token)
                .httpOnly(true)
                .path("/")
                .maxAge(3600)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, "http://localhost:4200/authentication/reset-password");
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());

        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token from cookie")
    public ResponseEntity<Void> resetPassword(
            @CookieValue("reset_token") String token,
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(token, request);
        return ResponseEntity.noContent().build();

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP to email")
    public ResponseEntity<Void> resendOtp(@RequestBody Map<String, String> body) {
        authService.resendOtp(body.get("email"));
        return ResponseEntity.ok().build();
    }
}
