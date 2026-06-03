package talan.pfe.rulengine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import talan.pfe.rulengine.AbstractIntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration des endpoints d'authentification.
 * Utilise PostgreSQL via Testcontainers, Kafka et Mail mockés.
 */
@DisplayName("Auth — IT")
class AuthIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/auth";
    }

    @Test
    @DisplayName("POST /api/auth/login avec email inexistant → 401")
    void login_unknownEmail_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "email", "nobody@nowhere.com",
                "password", "wrong-password"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/auth/refresh avec token invalide → 401")
    void refresh_invalidToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("refreshToken", "this.is.not.a.valid.jwt");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/refresh",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/login sans body → 400")
    void login_emptyBody_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/login",
                HttpMethod.POST,
                new HttpEntity<>("{}", headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/auth (endpoint inconnu) → 404")
    void unknownAuthEndpoint_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/unknown", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
