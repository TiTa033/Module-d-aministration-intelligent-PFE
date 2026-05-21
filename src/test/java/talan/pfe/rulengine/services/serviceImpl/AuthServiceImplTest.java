package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.AuthResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.repositories.*;
import talan.pfe.rulengine.security.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService jwtService;
    @Mock CustomUserDetailsService userDetailsService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock MailService mailService;
    @Mock OtpService otpService;
    @Mock CaptchaService captchaService;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks AuthServiceImpl authService;

    private User userWithTenant(Long id, String email, Long tenantId) {
        Tenant t = Tenant.builder().id(tenantId).name("Acme").build();
        return User.builder().id(id).email(email).role(Role.ADMIN).tenant(t).build();
    }

    private User globalAdmin(Long id, String email) {
        return User.builder().id(id).email(email).role(Role.GLOBAL_ADMIN).tenant(null).build();
    }

    // ─── LOGIN ───────────────────────────────────────────────

    @Test
    void login_sendsOtpAndReturnsRequiresOtpTrue() {
        LoginRequest req = new LoginRequest("alice@acme.com", "pass", "captcha");
        User user = userWithTenant(1L, "alice@acme.com", 10L);

        doNothing().when(captchaService).verify("captcha");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("alice@acme.com")).thenReturn(Optional.of(user));
        when(otpService.generateAndStore("alice@acme.com")).thenReturn("123456");
        doNothing().when(mailService).sendOtpEmail("alice@acme.com", "123456");

        AuthResponse resp = authService.login(req);

        assertThat(resp.isRequiresOtp()).isTrue();
        assertThat(resp.getEmail()).isEqualTo("alice@acme.com");
        verify(otpService).generateAndStore("alice@acme.com");
        verify(mailService).sendOtpEmail("alice@acme.com", "123456");
    }

    @Test
    void login_verifiesCaptchaBeforeAuthentication() {
        LoginRequest req = new LoginRequest("alice@acme.com", "bad", null);
        doNothing().when(captchaService).verify(null);
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad"));

        // authentication throws → login propagates the exception
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(Exception.class);
        verify(captchaService).verify(null);
    }

    // ─── VERIFY OTP ──────────────────────────────────────────

    @Test
    void verifyOtp_returnsFullTokensWhenOtpValid() {
        VerifyOtpRequest req = new VerifyOtpRequest("alice@acme.com", "123456");
        User user = userWithTenant(1L, "alice@acme.com", 10L);
        UserDetails ud = mock(UserDetails.class);
        RefreshToken rt = RefreshToken.builder().token("rt-token").user(user).build();

        doNothing().when(otpService).verify("alice@acme.com", "123456");
        when(userRepository.findByEmail("alice@acme.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("alice@acme.com")).thenReturn(ud);
        when(jwtService.generateAccessToken(ud, "10", "ADMIN")).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(rt);

        AuthResponse resp = authService.verifyOtp(req);

        assertThat(resp.getAccessToken()).isEqualTo("access-token");
        assertThat(resp.getRefreshToken()).isEqualTo("rt-token");
        assertThat(resp.getEmail()).isEqualTo("alice@acme.com");
        assertThat(resp.getTenantId()).isEqualTo("10");
    }

    @Test
    void verifyOtp_throwsWhenUserNotFound() {
        VerifyOtpRequest req = new VerifyOtpRequest("ghost@acme.com", "000");
        doNothing().when(otpService).verify("ghost@acme.com", "000");
        when(userRepository.findByEmail("ghost@acme.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyOtp(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ─── REGISTER ────────────────────────────────────────────

    @Test
    void register_nonGlobalAdmin_requiresTenantId() {
        RegisterRequest req = new RegisterRequest("Alice", "Smith", "a@b.com", "password8", null, Role.ADMIN);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID is required");
    }

    @Test
    void register_nonGlobalAdmin_failsWhenEmailAlreadyInTenant() {
        RegisterRequest req = new RegisterRequest("Alice", "Smith", "a@b.com", "password8", "5", Role.ADMIN);
        when(userRepository.existsByEmailAndTenantId("a@b.com", 5L)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists in this tenant");
    }

    @Test
    void register_nonGlobalAdmin_failsWhenTenantNotFound() {
        RegisterRequest req = new RegisterRequest("Alice", "Smith", "a@b.com", "password8", "5", Role.ADMIN);
        when(userRepository.existsByEmailAndTenantId("a@b.com", 5L)).thenReturn(false);
        when(tenantRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    void register_globalAdmin_failsWhenTenantIdProvided() {
        RegisterRequest req = new RegisterRequest("Admin", "G", "g@x.com", "password8", "5", Role.GLOBAL_ADMIN);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GLOBAL_ADMIN cannot have a tenant");
    }

    @Test
    void register_globalAdmin_failsWhenEmailAlreadyExists() {
        RegisterRequest req = new RegisterRequest("Admin", "G", "g@x.com", "password8", null, Role.GLOBAL_ADMIN);
        when(userRepository.existsByEmail("g@x.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    void register_globalAdmin_createsUserWithNullTenant() {
        RegisterRequest req = new RegisterRequest("Admin", "G", "g@x.com", "password8", null, Role.GLOBAL_ADMIN);
        when(userRepository.existsByEmail("g@x.com")).thenReturn(false);
        when(passwordEncoder.encode("password8")).thenReturn("hashed");

        User saved = globalAdmin(1L, "g@x.com");
        UserDetails ud = mock(UserDetails.class);
        RefreshToken rt = RefreshToken.builder().token("rt").user(saved).build();

        when(userRepository.save(any())).thenReturn(saved);
        when(userDetailsService.loadUserByUsername("g@x.com")).thenReturn(ud);
        when(jwtService.generateAccessToken(ud, null, "GLOBAL_ADMIN")).thenReturn("at");
        when(refreshTokenService.createRefreshToken(any())).thenReturn(rt);

        AuthResponse resp = authService.register(req);

        assertThat(resp.getTenantId()).isNull();
        assertThat(resp.getRole()).isEqualTo("GLOBAL_ADMIN");
    }

    @Test
    void register_defaultsToViewerWhenRoleNull() {
        RegisterRequest req = new RegisterRequest("Bob", "Lee", "b@t.com", "password8", "3", null);
        Tenant tenant = Tenant.builder().id(3L).name("T").build();
        when(userRepository.existsByEmailAndTenantId("b@t.com", 3L)).thenReturn(false);
        when(tenantRepository.findById(3L)).thenReturn(Optional.of(tenant));
        when(passwordEncoder.encode("password8")).thenReturn("hash");

        User saved = User.builder().id(2L).email("b@t.com").role(Role.VIEWER).tenant(tenant).build();
        UserDetails ud = mock(UserDetails.class);
        RefreshToken rt = RefreshToken.builder().token("rt").user(saved).build();
        when(userRepository.save(any())).thenReturn(saved);
        when(userDetailsService.loadUserByUsername("b@t.com")).thenReturn(ud);
        when(jwtService.generateAccessToken(any(), eq("3"), eq("VIEWER"))).thenReturn("at");
        when(refreshTokenService.createRefreshToken(any())).thenReturn(rt);

        AuthResponse resp = authService.register(req);
        assertThat(resp.getRole()).isEqualTo("VIEWER");
    }

    // ─── REFRESH ─────────────────────────────────────────────

    @Test
    void refresh_returnsNewTokensOnValidRefreshToken() {
        User user = userWithTenant(1L, "a@b.com", 5L);
        RefreshToken newRt = RefreshToken.builder().token("new-rt").user(user).build();
        UserDetails ud = mock(UserDetails.class);

        RefreshTokenRequest req = new RefreshTokenRequest("old-rt");
        when(refreshTokenService.verifyAndRotate("old-rt")).thenReturn(newRt);
        when(userDetailsService.loadUserByUsername("a@b.com")).thenReturn(ud);
        when(jwtService.generateAccessToken(ud, "5", "ADMIN")).thenReturn("new-at");

        AuthResponse resp = authService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-at");
        assertThat(resp.getRefreshToken()).isEqualTo("new-rt");
        assertThat(resp.getTenantId()).isEqualTo("5");
    }

    @Test
    void refresh_globalAdminHasNullTenantId() {
        User admin = globalAdmin(1L, "g@x.com");
        RefreshToken rt = RefreshToken.builder().token("rt").user(admin).build();
        UserDetails ud = mock(UserDetails.class);

        when(refreshTokenService.verifyAndRotate("rt")).thenReturn(rt);
        when(userDetailsService.loadUserByUsername("g@x.com")).thenReturn(ud);
        when(jwtService.generateAccessToken(ud, null, "GLOBAL_ADMIN")).thenReturn("at");

        AuthResponse resp = authService.refresh(new RefreshTokenRequest("rt"));
        assertThat(resp.getTenantId()).isNull();
    }

    // ─── LOGOUT ──────────────────────────────────────────────

    @Test
    void logout_revokesAllTokensForUser() {
        User user = userWithTenant(1L, "a@b.com", 5L);
        RefreshToken rt = RefreshToken.builder().token("my-rt").user(user).build();

        when(refreshTokenRepository.findByToken("my-rt")).thenReturn(Optional.of(rt));
        doNothing().when(refreshTokenRepository).revokeAllUserTokens(user);

        authService.logout(new RefreshTokenRequest("my-rt"));

        verify(refreshTokenRepository).revokeAllUserTokens(user);
    }

    @Test
    void logout_throwsWhenTokenNotFound() {
        when(refreshTokenRepository.findByToken("bad-rt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout(new RefreshTokenRequest("bad-rt")))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Refresh token not found");
    }

    // ─── FORGOT PASSWORD ─────────────────────────────────────

    @Test
    void forgotPassword_sendsResetEmailWhenUserExists() {
        User user = userWithTenant(1L, "a@b.com", 5L);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doNothing().when(mailService).send(eq("a@b.com"), any(), any());

        authService.forgotPassword(new ForgotPasswordRequest("a@b.com"), "http://front/reset");

        verify(mailService).send(eq("a@b.com"), any(), contains("http://front/reset"));
        verify(passwordResetTokenRepository).save(any());
    }

    @Test
    void forgotPassword_doesNothingWhenUserNotFound() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("ghost@x.com"), "http://x");

        verify(mailService, never()).send(any(), any(), any());
    }

    // ─── RESET PASSWORD ──────────────────────────────────────

    @Test
    void resetPassword_throwsWhenTokenInvalid() {
        when(passwordResetTokenRepository.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad", new ResetPasswordRequest("newPass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token invalide");
    }

    @Test
    void resetPassword_throwsWhenTokenAlreadyUsed() {
        User user = userWithTenant(1L, "a@b.com", 5L);
        PasswordResetToken token = PasswordResetToken.builder()
                .token("tok").user(user).used(true)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(passwordResetTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("tok", new ResetPasswordRequest("newPass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token expiré ou déjà utilisé");
    }

    @Test
    void resetPassword_throwsWhenTokenExpired() {
        User user = userWithTenant(1L, "a@b.com", 5L);
        PasswordResetToken token = PasswordResetToken.builder()
                .token("tok").user(user).used(false)
                .expiresAt(LocalDateTime.now().minusHours(2)).build();
        when(passwordResetTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("tok", new ResetPasswordRequest("newPass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token expiré ou déjà utilisé");
    }

    @Test
    void resetPassword_encodesPasswordAndMarksTokenUsed() {
        User user = userWithTenant(1L, "a@b.com", 5L);
        PasswordResetToken token = PasswordResetToken.builder()
                .token("tok").user(user).used(false)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(passwordResetTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPass8")).thenReturn("encoded");

        authService.resetPassword("tok", new ResetPasswordRequest("newPass8"));

        assertThat(token.isUsed()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("encoded");
    }

    // ─── RESEND OTP ──────────────────────────────────────────

    @Test
    void resendOtp_throwsWhenUserNotFound() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resendOtp("ghost@x.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resendOtp_generatesNewOtpAndSendsMail() {
        User user = userWithTenant(1L, "a@b.com", 5L);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(otpService.generateAndStore("a@b.com")).thenReturn("654321");
        doNothing().when(mailService).sendOtpEmail("a@b.com", "654321");

        authService.resendOtp("a@b.com");

        verify(otpService).generateAndStore("a@b.com");
        verify(mailService).sendOtpEmail("a@b.com", "654321");
    }
}
