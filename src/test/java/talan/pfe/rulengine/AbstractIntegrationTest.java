package talan.pfe.rulengine;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.security.JwtService;

import java.util.List;

/**
 * Classe de base pour tous les tests d'intégration.
 * Lance un container PostgreSQL réel via Testcontainers.
 * Kafka, Mail et Spring AI sont mockés.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // ── Mocks des dépendances externes ────────────────────────────────────────

    @MockBean AuditProducer        auditProducer;
    @MockBean NotificationProducer notificationProducer;
    @MockBean JavaMailSender       mailSender;

    // ── Utilitaire JWT ────────────────────────────────────────────────────────

    @Autowired JwtService jwtService;

    /**
     * Génère un Bearer token de test sans passer par le flow de login.
     *
     * @param email    email de l'utilisateur de test
     * @param tenantId identifiant du tenant (en String)
     * @param role     rôle (ex. "ADMIN", "GLOBAL_ADMIN")
     */
    protected String bearerToken(String email, String tenantId, String role) {
        User principal = new User(
                email, "",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return "Bearer " + jwtService.generateAccessToken(principal, tenantId, role);
    }
}
