package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;

    @InjectMocks RefreshTokenService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 3_600_000L);
    }

    private User user() {
        return User.builder().id(1L).email("user@test.com")
                .role(Role.ADMIN)
                .tenant(Tenant.builder().id(10L).name("Acme").build())
                .active(true).build();
    }

    private RefreshToken validToken(User u) {
        return RefreshToken.builder()
                .id(1L).token("valid-token").user(u)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .revoked(false)
                .build();
    }

    private RefreshToken revokedToken(User u) {
        return RefreshToken.builder()
                .id(2L).token("revoked-token").user(u)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .revoked(true)
                .build();
    }

    private RefreshToken expiredToken(User u) {
        return RefreshToken.builder()
                .id(3L).token("expired-token").user(u)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .revoked(false)
                .build();
    }

    // ─── createRefreshToken ───────────────────────────────────

    @Test
    void createRefreshToken_revokesExistingAndSavesNew() {
        User u = user();
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken result = service.createRefreshToken(u);

        verify(refreshTokenRepository).revokeAllUserTokens(u);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(result.getToken()).isNotNull().isNotBlank();
        assertThat(result.getUser()).isEqualTo(u);
        assertThat(result.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void createRefreshToken_tokenIsRandomUuid() {
        User u = user();
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken t1 = service.createRefreshToken(u);
        RefreshToken t2 = service.createRefreshToken(u);

        assertThat(t1.getToken()).isNotEqualTo(t2.getToken());
    }

    // ─── verifyAndRotate ──────────────────────────────────────

    @Test
    void verifyAndRotate_whenTokenNotFound_throwsTokenException() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndRotate("missing"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void verifyAndRotate_whenTokenRevoked_revokesAllAndThrows() {
        User u = user();
        RefreshToken revoked = revokedToken(u);
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.verifyAndRotate("revoked-token"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("revoked");

        verify(refreshTokenRepository).revokeAllUserTokens(u);
    }

    @Test
    void verifyAndRotate_whenTokenExpired_revokesAllAndThrows() {
        User u = user();
        RefreshToken expired = expiredToken(u);
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verifyAndRotate("expired-token"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("expired");

        verify(refreshTokenRepository).revokeAllUserTokens(u);
    }

    @Test
    void verifyAndRotate_whenValid_revokesOldAndReturnsNew() {
        User u = user();
        RefreshToken valid = validToken(u);
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken result = service.verifyAndRotate("valid-token");

        assertThat(valid.isRevoked()).isTrue();
        verify(refreshTokenRepository, atLeastOnce()).save(any(RefreshToken.class));
        assertThat(result.getUser()).isEqualTo(u);
        assertThat(result.getToken()).isNotEqualTo("valid-token");
    }
}
