package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;

    @InjectMocks RefreshTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 604_800_000L);
        user = User.builder().id(1L).email("u@test.com").build();
    }

    @Test
    @DisplayName("createRefreshToken() révoque les anciens tokens")
    void createRefreshToken() {
        RefreshToken saved = RefreshToken.builder().token("new-token").user(user).build();
        when(refreshTokenRepository.save(any())).thenReturn(saved);

        RefreshToken result = service.createRefreshToken(user);

        verify(refreshTokenRepository).revokeAllUserTokens(user);
        assertThat(result.getToken()).isEqualTo("new-token");
    }

    @Test
    @DisplayName("verifyAndRotate() émet un nouveau token si valide")
    void verifyAndRotate_valid() {
        RefreshToken existing = RefreshToken.builder()
                .token("old")
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByToken("old")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken rotated = service.verifyAndRotate("old");

        verify(refreshTokenRepository).save(existing);
        assertThat(existing.isRevoked()).isTrue();
        assertThat(rotated.getToken()).isNotBlank();
    }

    @Test
    @DisplayName("verifyAndRotate() échoue si token inconnu")
    void verifyAndRotate_notFound() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndRotate("missing"))
                .isInstanceOf(TokenException.class);
    }

    @Test
    @DisplayName("verifyAndRotate() révoque tout si token expiré")
    void verifyAndRotate_expired() {
        RefreshToken expired = RefreshToken.builder()
                .token("exp")
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByToken("exp")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verifyAndRotate("exp"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("expired");

        verify(refreshTokenRepository).revokeAllUserTokens(user);
    }
}
