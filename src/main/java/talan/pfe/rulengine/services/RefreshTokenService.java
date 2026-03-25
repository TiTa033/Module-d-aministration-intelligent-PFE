package talan.pfe.rulengine.services;

import talan.pfe.rulengine.entites.RefreshToken;
import talan.pfe.rulengine.entites.User;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(User user);
    RefreshToken verifyAndRotate(String token);
}
