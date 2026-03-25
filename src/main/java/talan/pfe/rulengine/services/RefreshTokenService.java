package talan.pfe.rulengine.services;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Revoke all existing tokens for this user first
        refreshTokenRepository.revokeAllUserTokens(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now()
                        .plusSeconds(refreshTokenExpiration / 1000))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken verifyAndRotate(String token) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(token)
                .orElseThrow(() ->
                        new TokenException("Refresh token not found"));

        if (!refreshToken.isValid()) {
            // Revoke all tokens for this user — possible token theft
            refreshTokenRepository
                    .revokeAllUserTokens(refreshToken.getUser());
            throw new TokenException(
                    refreshToken.isRevoked()
                            ? "Refresh token has been revoked"
                            : "Refresh token has expired"
            );
        }

        // Revoke the used token — rotation strategy
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        // Issue a  new refresh token
        return createRefreshToken(refreshToken.getUser());
    }
}