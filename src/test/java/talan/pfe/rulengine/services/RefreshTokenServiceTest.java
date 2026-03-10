package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User mockUser;
    private RefreshToken validToken;
    private RefreshToken expiredToken;
    private RefreshToken revokedToken;

    @BeforeEach
    void setUp() {
        // Inject the @Value field manually since we're not loading Spring context
        ReflectionTestUtils.setField(
                refreshTokenService,
                "refreshTokenExpiration",
                604800000L
        );

        Tenant mockTenant = new Tenant();
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

        validToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(mockUser)
                .expiresAt(LocalDateTime.now().minusDays(1)) // already expired
                .revoked(false)
                .build();

        revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true) // already revoked
                .build();
    }

    // ─── CREATE TESTS ───────────────────────────────────────

    @Test
    void createRefreshToken_shouldRevokeOldTokensAndCreateNew() {
        // Arrange
        when(refreshTokenRepository.save(any())).thenReturn(validToken);

        // Act
        RefreshToken result = refreshTokenService.createRefreshToken(mockUser);

        // Assert
        assertNotNull(result);
        verify(refreshTokenRepository, times(1))
                .revokeAllUserTokens(mockUser);
        verify(refreshTokenRepository, times(1)).save(any());
    }

    @Test
    void createRefreshToken_shouldSetCorrectExpiration() {
        // Arrange
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RefreshToken result = refreshTokenService.createRefreshToken(mockUser);

        // Assert
        assertNotNull(result.getExpiresAt());
        assertTrue(result.getExpiresAt().isAfter(LocalDateTime.now()));
        assertFalse(result.isRevoked());
        assertEquals(mockUser, result.getUser());
    }

    @Test
    void createRefreshToken_shouldGenerateUniqueTokens() {
        // Arrange
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RefreshToken token1 = refreshTokenService.createRefreshToken(mockUser);
        RefreshToken token2 = refreshTokenService.createRefreshToken(mockUser);

        // Assert
        assertNotEquals(token1.getToken(), token2.getToken());
    }

    // ─── VERIFY AND ROTATE TESTS ────────────────────────────

    @Test
    void verifyAndRotate_withValidToken_shouldRevokeOldAndReturnNew() {
        // Arrange
        String tokenValue = validToken.getToken();
        RefreshToken newToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token(UUID.randomUUID().toString())
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenValue))
                .thenReturn(Optional.of(validToken));
        when(refreshTokenRepository.save(validToken))
                .thenReturn(validToken);
        when(refreshTokenRepository.save(argThat(t -> !t.equals(validToken))))
                .thenReturn(newToken);

        // Act
        RefreshToken result = refreshTokenService.verifyAndRotate(tokenValue);

        // Assert
        assertNotNull(result);
        assertTrue(validToken.isRevoked()); // old token revoked
        verify(refreshTokenRepository, times(2)).save(any()); // save revoked + save new
    }

    @Test
    void verifyAndRotate_withExpiredToken_shouldThrowTokenException() {
        // Arrange
        String tokenValue = expiredToken.getToken();
        when(refreshTokenRepository.findByToken(tokenValue))
                .thenReturn(Optional.of(expiredToken));

        // Act & Assert
        TokenException exception = assertThrows(TokenException.class,
                () -> refreshTokenService.verifyAndRotate(tokenValue));

        assertTrue(exception.getMessage().contains("expired"));
        verify(refreshTokenRepository, times(1))
                .revokeAllUserTokens(mockUser);
    }

    @Test
    void verifyAndRotate_withRevokedToken_shouldThrowTokenException() {
        // Arrange
        String tokenValue = revokedToken.getToken();
        when(refreshTokenRepository.findByToken(tokenValue))
                .thenReturn(Optional.of(revokedToken));

        // Act & Assert
        TokenException exception = assertThrows(TokenException.class,
                () -> refreshTokenService.verifyAndRotate(tokenValue));

        assertTrue(exception.getMessage().contains("revoked"));
        // Security: revoke ALL tokens for this user (possible token theft)
        verify(refreshTokenRepository, times(1))
                .revokeAllUserTokens(mockUser);
    }

    @Test
    void verifyAndRotate_withNonExistingToken_shouldThrowTokenException() {
        // Arrange
        when(refreshTokenRepository.findByToken("fake-token"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TokenException.class,
                () -> refreshTokenService.verifyAndRotate("fake-token"));
    }

    // ─── TOKEN VALIDITY TESTS ───────────────────────────────

    @Test
    void refreshToken_isValid_whenNotRevokedAndNotExpired() {
        assertTrue(validToken.isValid());
    }

    @Test
    void refreshToken_isNotValid_whenExpired() {
        assertFalse(expiredToken.isValid());
    }

    @Test
    void refreshToken_isNotValid_whenRevoked() {
        assertFalse(revokedToken.isValid());
    }
}