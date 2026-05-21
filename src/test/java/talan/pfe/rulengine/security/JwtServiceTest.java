package talan.pfe.rulengine.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtServiceTest {

    private JwtService jwtService;

    // 64-char secret satisfies HS512 requirement
    private static final String SECRET =
            "test-secret-key-for-junit-tests-that-is-at-least-64-chars-ok!!";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900_000L);
    }

    private UserDetails userDetails(String username) {
        UserDetails ud = mock(UserDetails.class);
        when(ud.getUsername()).thenReturn(username);
        return ud;
    }

    // ─── GENERATE AND EXTRACT ─────────────────────────────────

    @Test
    void generateAccessToken_producesNonBlankToken() {
        UserDetails ud = userDetails("alice@acme.com");
        String token = jwtService.generateAccessToken(ud, "10", "ADMIN");
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsSubjectFromToken() {
        UserDetails ud = userDetails("alice@acme.com");
        String token = jwtService.generateAccessToken(ud, "10", "ADMIN");

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice@acme.com");
    }

    @Test
    void extractTenantId_returnsTenantFromClaims() {
        UserDetails ud = userDetails("alice@acme.com");
        String token = jwtService.generateAccessToken(ud, "42", "ADMIN");

        assertThat(jwtService.extractTenantId(token)).isEqualTo("42");
    }

    @Test
    void extractTenantId_returnsNullForGlobalAdmin() {
        UserDetails ud = userDetails("g@x.com");
        String token = jwtService.generateAccessToken(ud, null, "GLOBAL_ADMIN");

        assertThat(jwtService.extractTenantId(token)).isNull();
    }

    @Test
    void extractRole_returnsRoleFromClaims() {
        UserDetails ud = userDetails("alice@acme.com");
        String token = jwtService.generateAccessToken(ud, "10", "MANAGER");

        assertThat(jwtService.extractRole(token)).isEqualTo("MANAGER");
    }

    // ─── VALIDATION ──────────────────────────────────────────

    @Test
    void isTokenValid_returnsTrueForMatchingUser() {
        UserDetails ud = userDetails("alice@acme.com");
        String token = jwtService.generateAccessToken(ud, "10", "ADMIN");

        assertThat(jwtService.isTokenValid(token, ud)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForDifferentUser() {
        UserDetails alice = userDetails("alice@acme.com");
        UserDetails bob = userDetails("bob@acme.com");
        String token = jwtService.generateAccessToken(alice, "10", "ADMIN");

        assertThat(jwtService.isTokenValid(token, bob)).isFalse();
    }

    @Test
    void isTokenValid_throwsForExpiredToken() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        // -1000ms → already expired
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1000L);

        UserDetails ud = userDetails("alice@acme.com");
        String token = jwtService.generateAccessToken(ud, "10", "ADMIN");

        // parseSignedClaims() throws ExpiredJwtException for expired tokens
        assertThatThrownBy(() -> jwtService.isTokenValid(token, ud))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    // ─── DIFFERENT TENANTS/ROLES ──────────────────────────────

    @Test
    void twoTokensForSameUserHaveDifferentRoles() {
        UserDetails ud = userDetails("alice@acme.com");
        String adminToken = jwtService.generateAccessToken(ud, "10", "ADMIN");
        String viewerToken = jwtService.generateAccessToken(ud, "10", "VIEWER");

        assertThat(jwtService.extractRole(adminToken)).isEqualTo("ADMIN");
        assertThat(jwtService.extractRole(viewerToken)).isEqualTo("VIEWER");
    }
}