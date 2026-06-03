package talan.pfe.rulengine.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import talan.pfe.rulengine.AbstractIntegrationTest;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test E2E — parcours évaluation.
 * Vérifie que l'endpoint /api/evaluate respecte les règles de sécurité.
 */
@DisplayName("Evaluation — E2E")
class EvaluationE2ETest extends AbstractIntegrationTest {

    @LocalServerPort int port;

    @Autowired TestRestTemplate restTemplate;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository   userRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("E2E Eval Tenant")
                .slug("e2e-eval-" + System.nanoTime())
                .status(TenantStatus.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .email("eval-admin@e2e.com")
                .passwordHash("$2a$10$hashplaceholder")
                .name("Eval Admin")
                .role(Role.ADMIN)
                .tenant(tenant)
                .active(true)
                .build());

        adminToken = bearerToken("eval-admin@e2e.com", tenant.getId().toString(), "ADMIN");
    }

    private String evalUrl() {
        return "http://localhost:" + port + "/api/evaluate";
    }

    @Test
    @DisplayName("POST /api/evaluate sans authentification → 4xx")
    void evaluate_noAuth_returns4xx() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                { "input": { "age": 30, "income": 50000 } }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                evalUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("POST /api/evaluate avec une API key invalide → 4xx")
    void evaluate_invalidApiKey_returns4xx() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "invalid-api-key-xyz");

        String body = """
                { "input": { "age": 30, "income": 50000 } }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                evalUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        // L'API key est invalide : soit 401 (rejeté par le filtre)
        // soit 400 (si ruleSetId manquant et requête validée) — dans tous les cas 4xx
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("POST /api/evaluate avec token valide mais body vide → 400")
    void evaluate_authenticatedButEmptyBody_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", adminToken);

        // input manquant (required field)
        String body = "{}";

        ResponseEntity<String> response = restTemplate.exchange(
                evalUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
