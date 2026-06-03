package talan.pfe.rulengine.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.enums.TenantStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration des requêtes JPQL complexes de EvaluationRequestRepository.
 * Utilise un container PostgreSQL réel car les agrégations JSON (jsonb)
 * et les GROUP BY ne sont pas supportés fidèlement par H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("EvaluationRequestRepository — IT (PostgreSQL)")
class EvaluationRequestRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                          POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",                     POSTGRES::getUsername);
        registry.add("spring.datasource.password",                     POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto",                  () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",        () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.liquibase.enabled",                       () -> "false");
    }

    @Autowired TestEntityManager           em;
    @Autowired EvaluationRequestRepository repository;

    private Tenant  tenant;
    private RuleSet ruleSet;
    private ApiKey  apiKey;

    @BeforeEach
    void setUp() {
        tenant = em.persistAndFlush(Tenant.builder()
                .name("Test Tenant")
                .slug("test-tenant")
                .status(TenantStatus.ACTIVE)
                .build());

        ruleSet = em.persistAndFlush(RuleSet.builder()
                .name("Credit Scoring RS")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .status(RuleSetStatus.ACTIVE)
                .tenant(tenant)
                .build());

        apiKey = em.persistAndFlush(ApiKey.builder()
                .keyHash("hash-test-key-integration")
                .keyPrefix("test")
                .name("test-api-key")
                .active(true)
                .tenant(tenant)
                .ruleSet(ruleSet)
                .build());

        em.persistAndFlush(buildEvaluationPair(150L));
        em.persistAndFlush(buildEvaluationPair(250L));
        em.clear();
    }

    private EvaluationRequest buildEvaluationPair(long execMs) {
        EvaluationRequest req = EvaluationRequest.builder()
                .inputPayload("{\"age\":30}")
                .tenant(tenant)
                .ruleSet(ruleSet)
                .apiKey(apiKey)
                .build();

        EvaluationResult result = EvaluationResult.builder()
                .outputPayload("{\"approved\":true}")
                .matchedRules("[]")
                .executionTimeMs(execMs)
                .strategyUsed(EvaluationStrategy.FIRST_MATCH)
                .totalScore(0.8)
                .evaluationRequest(req)
                .build();

        req.setResult(result);
        return req;
    }

    // ── countByTenantSince ─────────────────────────────────────────────────────

    @Test
    @DisplayName("countByTenantSince() doit retourner le nombre d'évaluations du tenant")
    void countByTenantSince_returnsCorrectCount() {
        Long count = repository.countByTenantSince(tenant.getId(),
                LocalDateTime.now().minusHours(1));

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByTenantSince() hors fenêtre doit retourner 0")
    void countByTenantSince_futureWindow_returnsZero() {
        Long count = repository.countByTenantSince(tenant.getId(),
                LocalDateTime.now().plusHours(1));

        assertThat(count).isEqualTo(0L);
    }

    // ── avgExecutionMsByTenantSince ────────────────────────────────────────────

    @Test
    @DisplayName("avgExecutionMsByTenantSince() doit retourner la moyenne des temps d'exécution")
    void avgExecutionMs_returnsCorrectAverage() {
        Double avg = repository.avgExecutionMsByTenantSince(tenant.getId(),
                LocalDateTime.now().minusHours(1));

        assertThat(avg).isEqualTo(200.0); // (150 + 250) / 2
    }

    // ── countByRuleSetAndTenantSince ───────────────────────────────────────────

    @Test
    @DisplayName("countByRuleSetAndTenantSince() doit retourner le compte par ruleSet")
    void countByRuleSet_returnsCorrectCount() {
        Long count = repository.countByRuleSetAndTenantSince(
                ruleSet.getId(), tenant.getId(), LocalDateTime.now().minusHours(1));

        assertThat(count).isEqualTo(2L);
    }

    // ── findTopRuleSetsByTenant ────────────────────────────────────────────────

    @Test
    @DisplayName("findTopRuleSetsByTenant() doit retourner le ruleSet avec son compteur")
    void findTopRuleSets_returnsRuleSet() {
        List<Object[]> rows = repository.findTopRuleSetsByTenant(
                tenant.getId(),
                LocalDateTime.now().minusHours(1),
                PageRequest.of(0, 5));

        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row[0]).isEqualTo(ruleSet.getId());
        assertThat(row[1]).isEqualTo("Credit Scoring RS");
        assertThat(((Number) row[2]).longValue()).isEqualTo(2L);
    }

    // ── findDailyStatsByRuleSet ────────────────────────────────────────────────

    @Test
    @DisplayName("findDailyStatsByRuleSet() doit retourner les stats journalières")
    void findDailyStats_returnsAtLeastOneRow() {
        List<Object[]> rows = repository.findDailyStatsByRuleSet(
                ruleSet.getId(),
                tenant.getId(),
                LocalDateTime.now().minusHours(1));

        assertThat(rows).isNotEmpty();
        Object[] row = rows.get(0);
        assertThat(((Number) row[1]).longValue()).isEqualTo(2L); // count
        assertThat(((Number) row[2]).doubleValue()).isEqualTo(200.0); // avg exec ms
    }

    // ── countByRuleSetAndTenantBetween ─────────────────────────────────────────

    @Test
    @DisplayName("countByRuleSetAndTenantBetween() doit retourner le compte dans la fenêtre")
    void countByRuleSetBetween_returnsCorrectCount() {
        Long count = repository.countByRuleSetAndTenantBetween(
                ruleSet.getId(), tenant.getId(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));

        assertThat(count).isEqualTo(2L);
    }
}
