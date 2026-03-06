package talan.pfe.rulengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.TokenException;
import talan.pfe.rulengine.repositories.*;
import talan.pfe.rulengine.security.*;

import java.util.UUID;

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

    // ─── LOGIN ──────────────────────────────────────────────
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        return buildAuthResponse(user);
    }

    // ─── REGISTER ───────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists in this tenant
        if (userRepository.existsByEmailAndTenantId(
                request.getEmail(),
                UUID.fromString(request.getTenantId()))) {
            throw new IllegalArgumentException(
                    "Email already exists in this tenant");
        }

        Tenant tenant = tenantRepository
                .findById(UUID.fromString(request.getTenantId()))
                .orElseThrow(() ->
                        new IllegalArgumentException("Tenant not found"));

        Role role = request.getRole() != null ? request.getRole() : Role.VIEWER;

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .tenant(tenant)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    // ─── REFRESH ────────────────────────────────────────────
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        // Verify old token and get a new rotated one
        RefreshToken newRefreshToken = refreshTokenService
                .verifyAndRotate(request.getRefreshToken());

        User user = newRefreshToken.getUser();
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtService.generateAccessToken(
                userDetails,
                user.getTenant().getId().toString(),
                user.getRole().name()
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantId(user.getTenant().getId().toString())
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

    // ─── PRIVATE HELPER ─────────────────────────────────────
    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtService.generateAccessToken(
                userDetails,
                user.getTenant().getId().toString(),
                user.getRole().name()
        );

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .tenantId(user.getTenant().getId().toString())
                .accessTokenExpiresIn(900000L)
                .refreshTokenExpiresIn(604800000L)
                .build();
    }
}