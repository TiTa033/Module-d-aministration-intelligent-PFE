package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import talan.pfe.rulengine.dtos.request.AuthResponse;
import talan.pfe.rulengine.dtos.request.LoginRequest;
import talan.pfe.rulengine.dtos.request.RefreshTokenRequest;
import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.security.CustomUserDetailsService;
import talan.pfe.rulengine.security.JwtService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User mockUser;
    private Tenant mockTenant;
    private RefreshToken mockRefreshToken;
    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockTenant = new Tenant();
        mockTenant.setId(UUID.randomUUID());
        mockTenant.setName("BNP Paribas");
        mockTenant.setSlug("bnp");
        mockTenant.setStatus(TenantStatus.ACTIVE);

        mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setEmail("amine@bnp.com");
        mockUser.setPasswordHash("hashedpassword");
        mockUser.setRole(Role.ADMIN);
        mockUser.setActive(true);
        mockUser.setTenant(mockTenant);

        mockUserDetails = new org.springframework.security.core.userdetails.User(
                mockUser.getEmail(),
                mockUser.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        mockRefreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
    }

    // ─── LOGIN TESTS ────────────────────────────────────────

    @Test
    void login_withValidCredentials_shouldReturnAuthResponse() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("amine@bnp.com");
        request.setPassword("password123");

        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(
                        "amine@bnp.com", "password123"));
        when(userRepository.findByEmail("amine@bnp.com"))
                .thenReturn(Optional.of(mockUser));
        when(userDetailsService.loadUserByUsername("amine@bnp.com"))
                .thenReturn(mockUserDetails);
        when(jwtService.generateAccessToken(any(), any(), any()))
                .thenReturn("mock.access.token");
        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(mockRefreshToken);

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("mock.access.token", response.getAccessToken());
        assertEquals(mockRefreshToken.getToken(), response.getRefreshToken());
        assertEquals("amine@bnp.com", response.getEmail());
        assertEquals("ADMIN", response.getRole());

        verify(authenticationManager, times(1)).authenticate(any());
        verify(userRepository, times(1)).findByEmail("amine@bnp.com");
        verify(refreshTokenService, times(1)).createRefreshToken(mockUser);
    }

    @Test
    void login_withInvalidCredentials_shouldThrowException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("amine@bnp.com");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class,
                () -> authService.login(request));

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void login_shouldReturnCorrectTenantId() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setEmail("amine@bnp.com");
        request.setPassword("password123");

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("amine@bnp.com"))
                .thenReturn(Optional.of(mockUser));
        when(userDetailsService.loadUserByUsername(any()))
                .thenReturn(mockUserDetails);
        when(jwtService.generateAccessToken(any(), any(), any()))
                .thenReturn("mock.access.token");
        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(mockRefreshToken);

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertEquals(mockTenant.getId().toString(), response.getTenantId());
    }

    // ─── REFRESH TESTS ──────────────────────────────────────

    @Test
    void refresh_withValidToken_shouldReturnNewAuthResponse() {
        // Arrange
        String oldToken = mockRefreshToken.getToken();
        RefreshToken newRefreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenService.verifyAndRotate(oldToken))
                .thenReturn(newRefreshToken);
        when(userDetailsService.loadUserByUsername(mockUser.getEmail()))
                .thenReturn(mockUserDetails);
        when(jwtService.generateAccessToken(any(), any(), any()))
                .thenReturn("new.access.token");

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(oldToken);

        // Act
        AuthResponse response = authService.refresh(request);

        // Assert
        assertNotNull(response);
        assertEquals("new.access.token", response.getAccessToken());
        assertEquals(newRefreshToken.getToken(), response.getRefreshToken());
        verify(refreshTokenService, times(1)).verifyAndRotate(oldToken);
    }

    // ─── LOGOUT TESTS ───────────────────────────────────────

    @Test
    void logout_withValidToken_shouldRevokeAllUserTokens() {
        // Arrange
        String token = mockRefreshToken.getToken();
        when(refreshTokenRepository.findByToken(token))
                .thenReturn(Optional.of(mockRefreshToken));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);

        // Act
        authService.logout(request);

        // Assert
        verify(refreshTokenRepository, times(1))
                .revokeAllUserTokens(mockUser);
    }

    @Test
    void logout_withInvalidToken_shouldThrowException() {
        // Arrange
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("invalid-token");

        when(refreshTokenRepository.findByToken("invalid-token"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(Exception.class, () -> authService.logout(request));
    }
}