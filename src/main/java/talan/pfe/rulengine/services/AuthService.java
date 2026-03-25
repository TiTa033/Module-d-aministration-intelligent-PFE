package talan.pfe.rulengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.entites.PasswordResetToken;
import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.dtos.response.AuthResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.repositories.PasswordResetTokenRepository;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.security.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final CaptchaService captchaService;

    // ─── LOGIN ──────────────────────────────────────────────
    @Transactional
    public AuthResponse login(LoginRequest request) {
        captchaService.verify(request.getCaptchaToken());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        String otp = otpService.generateAndStore(user.getEmail());
        emailService.sendOtpEmail(user.getEmail(), otp);

        return AuthResponse.builder()
                .email(user.getEmail())
                .requiresOtp(true)
                .build();
    }

    // ─── VERIFY OTP ─────────────────────────────────────────
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        otpService.verify(request.getEmail(), request.getOtpCode());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"));

        return buildAuthResponse(user);
    }

    // ─── REGISTER ───────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        Role role = request.getRole() != null ? request.getRole() : Role.VIEWER;

        Tenant tenant = null;

        // ✅ NON GLOBAL USERS → tenant REQUIRED
        if (role != Role.GLOBAL_ADMIN) {

            if (request.getTenantId() == null || request.getTenantId().isBlank()) {
                throw new IllegalArgumentException("Tenant ID is required for this role");
            }

            Long tenantId = Long.parseLong(request.getTenantId());

            if (userRepository.existsByEmailAndTenantId(
                    request.getEmail(), tenantId)) {
                throw new IllegalArgumentException(
                        "Email already exists in this tenant");
            }

            tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Tenant not found"));
        }

        // ✅ GLOBAL ADMIN → NO tenant
        else {
            if (request.getTenantId() != null) {
                throw new IllegalArgumentException("GLOBAL_ADMIN cannot have a tenant");
            }

            // Optional but recommended
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email already exists");
            }
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .tenant(tenant) // null for GLOBAL_ADMIN
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    // ─── REFRESH ────────────────────────────────────────────
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken newRefreshToken = refreshTokenService
                .verifyAndRotate(request.getRefreshToken());

        User user = newRefreshToken.getUser();
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(user.getEmail());

        // ✅ Handle null tenant (GLOBAL_ADMIN case)
        String tenantId = user.getTenant() != null
                ? user.getTenant().getId().toString()
                : null;

        String accessToken = jwtService.generateAccessToken(
                userDetails,
                tenantId,
                user.getRole().name()
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantId(tenantId)
                .accessTokenExpiresIn(900000L)
                .refreshTokenExpiresIn(604800000L)
                .build();
    }

    // ─── LOGOUT ─────────────────────────────────────────────
    @Transactional
    public void logout(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() ->
                        new TokenException("Refresh token not found"));

        refreshTokenRepository.revokeAllUserTokens(refreshToken.getUser());
    }

    // ─── FORGOT PASSWORD ────────────────────────────────────
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request,
                               String resetBaseUrl) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            PasswordResetToken token = PasswordResetToken.builder()
                    .token(java.util.UUID.randomUUID().toString())
                    .user(user)
                    .expiresAt(java.time.LocalDateTime.now().plusHours(1))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(token);

            String resetLink = resetBaseUrl + "?token=" + token.getToken();
            String subject = "Réinitialisation de votre mot de passe";
            String text = "Bonjour,\n\nPour réinitialiser votre mot de passe, "
                    + "cliquez sur le lien suivant :\n"
                    + resetLink
                    + "\n\nCe lien est valable 1 heure.\n\nCordialement,\nRaaS Platform";

            mailService.send(user.getEmail(), subject, text);
        });
    }

    // ─── RESET PASSWORD ─────────────────────────────────────
    @Transactional
    public void resetPassword(String tokenValue,
                              ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByToken(tokenValue)
                .orElseThrow(() ->
                        new IllegalArgumentException("Token invalide"));

        if (token.isUsed() ||
                token.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Token expiré ou déjà utilisé");
        }

        User user = token.getUser();
        user.setPasswordHash(
                passwordEncoder.encode(request.getNewPassword()));
        token.setUsed(true);
    }

    // ─── RESEND OTP ─────────────────────────────────────────
    @Transactional
    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"));
        String otp = otpService.generateAndStore(user.getEmail());
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    // ─── PRIVATE HELPER ─────────────────────────────────────
    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(user.getEmail());

        // tenantId is null for GLOBAL_ADMIN
        String tenantId = user.getTenant() != null
                ? user.getTenant().getId().toString()
                : null;

        String accessToken = jwtService.generateAccessToken(
                userDetails,
                tenantId,
                user.getRole().name()
        );

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantId(tenantId)
                .accessTokenExpiresIn(900000L)
                .refreshTokenExpiresIn(604800000L)
                .build();
    }
}