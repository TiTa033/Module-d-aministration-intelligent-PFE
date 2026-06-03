package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.DisplayName;
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
import talan.pfe.rulengine.repositories.PasswordResetTokenRepository;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.security.CustomUserDetailsService;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.security.JwtService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
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

    @InjectMocks AuthServiceImpl service;

    @Test
    @DisplayName("login() déclenche l'envoi OTP")
    void login_sendsOtp() {
        User user = User.builder().email("u@test.com").build();
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(otpService.generateAndStore("u@test.com")).thenReturn("123456");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("u@test.com");
        loginRequest.setPassword("pwd");
        loginRequest.setCaptchaToken("t");
        AuthResponse response = service.login(loginRequest);

        verify(captchaService).verify("t");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(mailService).sendOtpEmail("u@test.com", "123456");
        assertThat(response.isRequiresOtp()).isTrue();
        assertThat(response.getEmail()).isEqualTo("u@test.com");
    }

    @Test
    @DisplayName("verifyOtp() retourne les tokens JWT")
    void verifyOtp_returnsTokens() {
        Tenant tenant = Tenant.builder().id(1L).build();
        User user = User.builder().email("u@test.com").role(Role.ADMIN).tenant(tenant).build();
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername("u@test.com").password("").roles("ADMIN").build();
        RefreshToken refresh = RefreshToken.builder().token("refresh-1").build();

        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("u@test.com")).thenReturn(details);
        when(jwtService.generateAccessToken(details, "1", "ADMIN")).thenReturn("access-1");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refresh);

        AuthResponse response = service.verifyOtp(
                new VerifyOtpRequest("u@test.com", "123456"));

        verify(otpService).verify("u@test.com", "123456");
        assertThat(response.getAccessToken()).isEqualTo("access-1");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-1");
    }

    @Test
    @DisplayName("logout() révoque les tokens utilisateur")
    void logout_revokesTokens() {
        User user = User.builder().id(1L).build();
        RefreshToken token = RefreshToken.builder().token("rt").user(user).build();
        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(token));

        service.logout(new RefreshTokenRequest("rt"));

        verify(refreshTokenRepository).revokeAllUserTokens(user);
    }

    @Test
    @DisplayName("logout() échoue si token inconnu")
    void logout_unknownToken() {
        when(refreshTokenRepository.findByToken("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.logout(new RefreshTokenRequest("x")))
                .isInstanceOf(TokenException.class);
    }

    @Test
    @DisplayName("resendOtp() régénère et envoie l'OTP")
    void resendOtp() {
        User user = User.builder().email("u@test.com").build();
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(otpService.generateAndStore("u@test.com")).thenReturn("999999");

        service.resendOtp("u@test.com");

        verify(mailService).sendOtpEmail("u@test.com", "999999");
    }

    @Test
    @DisplayName("resendOtp() utilisateur inconnu")
    void resendOtp_userNotFound() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendOtp("missing@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("refresh() retourne un nouveau couple de tokens")
    void refresh_returnsNewTokens() {
        User user = User.builder().email("u@test.com").role(Role.ADMIN).build();
        RefreshToken rotated = RefreshToken.builder().token("new-rt").user(user).build();
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername("u@test.com").password("").roles("ADMIN").build();

        when(refreshTokenService.verifyAndRotate("old-rt")).thenReturn(rotated);
        when(userDetailsService.loadUserByUsername("u@test.com")).thenReturn(details);
        when(jwtService.generateAccessToken(details, null, "ADMIN")).thenReturn("new-at");

        AuthResponse response = service.refresh(new RefreshTokenRequest("old-rt"));

        assertThat(response.getAccessToken()).isEqualTo("new-at");
        assertThat(response.getRefreshToken()).isEqualTo("new-rt");
    }

    @Test
    @DisplayName("forgotPassword() envoie un mail si utilisateur existe")
    void forgotPassword_sendsMail() {
        User user = User.builder().id(1L).email("u@test.com").build();
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.forgotPassword(
                new ForgotPasswordRequest("u@test.com"),
                "http://localhost/reset");

        verify(mailService).send(eq("u@test.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("resetPassword() met à jour le mot de passe")
    void resetPassword_success() {
        User user = User.builder().id(1L).passwordHash("old").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token("tok")
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(passwordResetTokenRepository.findByToken("tok")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPwd")).thenReturn("encoded");

        service.resetPassword("tok", new ResetPasswordRequest("newPwd"));

        assertThat(user.getPasswordHash()).isEqualTo("encoded");
        assertThat(token.isUsed()).isTrue();
    }
}
