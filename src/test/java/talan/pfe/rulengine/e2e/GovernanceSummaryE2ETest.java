package talan.pfe.rulengine.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Test E2E — tableau de bord de gouvernance.
 * Vérifie que le résumé de gouvernance retourne une structure cohérente
 * après insertion de données de test dans la base réelle.
 */
@DisplayName("GovernanceSummary — E2E")
class GovernanceSummaryE2ETest extends AbstractIntegrationTest {

    @LocalServerPort int port;

    @Autowired TestRestTemplate restTemplate;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository   userRepository;
    @Autowired ObjectMapper     objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Gov E2E Tenant")
                .slug("gov-e2e-" + System.nanoTime())
                .status(TenantStatus.ACTIVE)
                .build());

        userRepository.save(User.builder()
                .email("gov-admin@e2e.com")
                .passwordHash("$2a$10$hashplaceholder")
                .name("Gov Admin")
                .role(Role.ADMIN)
                .tenant(tenant)
                .active(true)
                .build());

        adminToken = bearerToken(
                "gov-admin@e2e.com",
                tenant.getId().toString(),
                "ADMIN");
    }

    private String summaryUrl() {
        return "http://localhost:" + port + "/api/governance/summary";
    }

    @Test
    @DisplayName("GET /api/governance/summary sans token → 403")
    void summary_withoutToken_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(summaryUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /api/governance/summary avec token ADMIN → 200 + structure complète")
    void summary_withAdminToken_returns200WithStructure() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                summaryUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        JsonNode body = objectMapper.readTree(response.getBody());

        // Structure attendue : les champs sont présents (valeur 0 pour un tenant vide)
        assertThat(body.has("activeRuleSets")).isTrue();
        assertThat(body.has("totalEvaluationsToday")).isTrue();
        assertThat(body.has("avgExecutionMsToday")).isTrue();
        assertThat(body.has("activeAlerts")).isTrue();
        assertThat(body.has("unreadNotifications")).isTrue();
        assertThat(body.has("topRuleSets")).isTrue();
        assertThat(body.has("charts")).isTrue();
        assertThat(body.get("charts").has("evaluationsTrend")).isTrue();
        assertThat(body.get("charts").has("strategiesByType")).isTrue();

        assertThat(body.get("activeRuleSets").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(body.get("topRuleSets").isArray()).isTrue();
    }

    @Test
    @DisplayName("Headers OWASP présents sur la réponse de gouvernance")
    void summary_owaspHeadersPresent() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                summaryUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        HttpHeaders responseHeaders = response.getHeaders();

        assertThat(responseHeaders.getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(responseHeaders.getFirst("X-Frame-Options"))
                .isEqualTo("DENY");
        assertThat(responseHeaders.containsKey("Content-Security-Policy")).isTrue();
        assertThat(responseHeaders.containsKey("Referrer-Policy")).isTrue();
        assertThat(responseHeaders.containsKey("Permissions-Policy")).isTrue();
    }
}
