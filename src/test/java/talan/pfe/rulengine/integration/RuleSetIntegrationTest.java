package talan.pfe.rulengine.integration;

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
 * Tests d'intégration des endpoints RuleSet.
 * Vérifie les règles d'accès et le comportement avec un JWT généré directement.
 */
@DisplayName("RuleSet — IT")
class RuleSetIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort int port;

    @Autowired TestRestTemplate  restTemplate;
    @Autowired TenantRepository  tenantRepository;
    @Autowired UserRepository    userRepository;

    private String adminToken;
    private Long   tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Integration Tenant")
                .slug("integration-tenant-" + System.nanoTime())
                .status(TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();

        userRepository.save(User.builder()
                .email("admin-it@test.com")
                .passwordHash("$2a$10$hashplaceholder")
                .name("Admin IT")
                .role(Role.ADMIN)
                .tenant(tenant)
                .active(true)
                .build());

        adminToken = bearerToken("admin-it@test.com", tenantId.toString(), "ADMIN");
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/rulesets";
    }

    @Test
    @DisplayName("GET /api/rulesets sans token → 403")
    void list_withoutToken_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /api/rulesets avec token ADMIN → 200")
    void list_withAdminToken_returns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("POST /api/rulesets avec token ADMIN → 201")
    void create_withAdminToken_returns201() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", adminToken);

        String body = """
                {
                  "name": "IT RuleSet",
                  "description": "Test d'intégration",
                  "evaluationStrategy": "FIRST_MATCH"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("GET /api/rulesets/{id} inexistant → 404")
    void getById_notFound_returns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/999999", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
