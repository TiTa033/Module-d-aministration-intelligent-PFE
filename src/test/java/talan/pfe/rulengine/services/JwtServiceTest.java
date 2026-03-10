package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.security.JwtService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;

    private UserDetails mockUserDetails;
    private String tenantId;
    private String role;

    // same secret format as your application.properties
    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // Inject @Value fields manually
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L);

        tenantId = UUID.randomUUID().toString();
        role = "ADMIN";

        mockUserDetails = new org.springframework.security.core.userdetails.User(
                "amine@bnp.com",
                "hashedpassword",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    // ─── GENERATE TOKEN TESTS ───────────────────────────────

    @Test
    void generateAccessToken_shouldReturnNonNullToken() {
        // Act
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateAccessToken_shouldReturnValidJwtFormat() {
        // Act
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        // Assert — JWT has 3 parts separated by dots
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
    }

    // ─── EXTRACT CLAIMS TESTS ───────────────────────────────

    @Test
    void extractUsername_shouldReturnCorrectEmail() {
        // Arrange
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        // Act
        String extractedEmail = jwtService.extractUsername(token);

        // Assert
        assertEquals("amine@bnp.com", extractedEmail);
    }

    @Test
    void extractTenantId_shouldReturnCorrectTenantId() {
        // Arrange
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        // Act
        String extractedTenantId = jwtService.extractTenantId(token);

        // Assert
        assertEquals(tenantId, extractedTenantId);
    }

    @Test
    void extractRole_shouldReturnCorrectRole() {
        // Arrange
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        // Act
        String extractedRole = jwtService.extractRole(token);

        // Assert
        assertEquals("ADMIN", extractedRole);
    }

    // ─── TOKEN VALIDATION TESTS ─────────────────────────────

    @Test
    void isTokenValid_withValidToken_shouldReturnTrue() {
        // Arrange
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        // Act
        boolean isValid = jwtService.isTokenValid(token, mockUserDetails);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void isTokenValid_withWrongUser_shouldReturnFalse() {
        // Arrange
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        UserDetails differentUser =
                new org.springframework.security.core.userdetails.User(
                        "other@bnp.com",
                        "hashedpassword",
                        List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))
                );

        // Act
        boolean isValid = jwtService.isTokenValid(token, differentUser);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void isTokenValid_withExpiredToken_shouldReturnFalse() {
        // Arrange — set expiration to 0ms so token is immediately expired
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 0L);
        String token = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);

        // Reset expiration back to normal
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L);

        // Act & Assert
        assertThrows(Exception.class,
                () -> jwtService.isTokenValid(token, mockUserDetails));
    }

    @Test
    void generateAccessToken_differentUsers_shouldReturnDifferentTokens() {
        // Arrange
        UserDetails anotherUser =
                new org.springframework.security.core.userdetails.User(
                        "other@bnp.com",
                        "hashedpassword",
                        List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))
                );

        // Act
        String token1 = jwtService.generateAccessToken(
                mockUserDetails, tenantId, role);
        String token2 = jwtService.generateAccessToken(
                anotherUser, tenantId, "VIEWER");

        // Assert
        assertNotEquals(token1, token2);
    }
}