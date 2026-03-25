package talan.pfe.rulengine.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, " +
            "r.revokedAt = CURRENT_TIMESTAMP " +
            "WHERE r.user = :user AND r.revoked = false")
    void revokeAllUserTokens(User user);

    @Modifying
    void deleteByUser(User user);
}