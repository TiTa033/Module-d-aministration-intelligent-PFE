package talan.pfe.rulengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.EvaluationRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRequestRepository
        extends JpaRepository<EvaluationRequest, Long>,
        JpaSpecificationExecutor<EvaluationRequest> {

    @Query("""
        SELECT er FROM EvaluationRequest er
        LEFT JOIN FETCH er.result
        LEFT JOIN FETCH er.ruleSet
        LEFT JOIN FETCH er.apiKey
        LEFT JOIN FETCH er.tenant
        WHERE er.id = :id AND er.tenant.id = :tenantId
    """)
    Optional<EvaluationRequest> findByIdAndTenantId(
            @Param("id")       Long id,
            @Param("tenantId") Long tenantId);

    @Query("SELECT er FROM EvaluationRequest er WHERE er.tenant.id = :tenantId ORDER BY er.requestedAt DESC")
    Page<EvaluationRequest> findByTenantIdOrderByRequestedAtDesc(
            @Param("tenantId") Long tenantId, Pageable pageable);

    // ─── T-018 : Métriques par RuleSet ────────────────────────────────────────

    @Query("""
        SELECT CAST(er.requestedAt AS date),
               COUNT(er.id),
               AVG(er.result.executionTimeMs)
        FROM EvaluationRequest er
        WHERE er.ruleSet.id = :ruleSetId
          AND er.tenant.id  = :tenantId
          AND er.requestedAt >= :from
        GROUP BY CAST(er.requestedAt AS date)
        ORDER BY CAST(er.requestedAt AS date) ASC
    """)
    List<Object[]> findDailyStatsByRuleSet(
            @Param("ruleSetId") Long ruleSetId,
            @Param("tenantId")  Long tenantId,
            @Param("from")      LocalDateTime from);

    @Query("""
        SELECT COUNT(er.id)
        FROM EvaluationRequest er
        WHERE er.ruleSet.id  = :ruleSetId
          AND er.tenant.id   = :tenantId
          AND er.requestedAt >= :from
    """)
    Long countByRuleSetAndTenantSince(
            @Param("ruleSetId") Long ruleSetId,
            @Param("tenantId")  Long tenantId,
            @Param("from")      LocalDateTime from);

    @Query("""
        SELECT AVG(er.result.executionTimeMs)
        FROM EvaluationRequest er
        WHERE er.ruleSet.id  = :ruleSetId
          AND er.tenant.id   = :tenantId
          AND er.requestedAt >= :from
    """)
    Double avgExecutionMsByRuleSetSince(
            @Param("ruleSetId") Long ruleSetId,
            @Param("tenantId")  Long tenantId,
            @Param("from")      LocalDateTime from);

    // ─── T-019 : Gouvernance (KPIs globaux par tenant) ───────────────────────

    @Query("""
        SELECT COUNT(er.id)
        FROM EvaluationRequest er
        WHERE er.tenant.id   = :tenantId
          AND er.requestedAt >= :from
    """)
    Long countByTenantSince(
            @Param("tenantId") Long tenantId,
            @Param("from")     LocalDateTime from);

    @Query("""
        SELECT AVG(er.result.executionTimeMs)
        FROM EvaluationRequest er
        WHERE er.tenant.id   = :tenantId
          AND er.requestedAt >= :from
    """)
    Double avgExecutionMsByTenantSince(
            @Param("tenantId") Long tenantId,
            @Param("from")     LocalDateTime from);

    // Top 5 RuleSets les plus utilisés
    @Query("""
        SELECT er.ruleSet.id, er.ruleSet.name, COUNT(er.id)
        FROM EvaluationRequest er
        WHERE er.tenant.id   = :tenantId
          AND er.requestedAt >= :from
        GROUP BY er.ruleSet.id, er.ruleSet.name
        ORDER BY COUNT(er.id) DESC
    """)
    List<Object[]> findTopRuleSetsByTenant(
            @Param("tenantId") Long tenantId,
            @Param("from")     LocalDateTime from,
            Pageable pageable);

    @Query("""
        SELECT CAST(er.requestedAt AS date), COUNT(er.id)
        FROM EvaluationRequest er
        WHERE er.tenant.id = :tenantId
          AND er.requestedAt >= :from
        GROUP BY CAST(er.requestedAt AS date)
        ORDER BY CAST(er.requestedAt AS date) ASC
        """)
    List<Object[]> findDailyEvaluationsByTenant(
            @Param("tenantId") Long tenantId,
            @Param("from")     LocalDateTime from);

    @Query("""
        SELECT er.result.strategyUsed, COUNT(er.id)
        FROM EvaluationRequest er
        WHERE er.tenant.id = :tenantId
          AND er.requestedAt >= :from
          AND er.result IS NOT NULL
        GROUP BY er.result.strategyUsed
        """)
    List<Object[]> findStrategyCountsByTenant(
            @Param("tenantId") Long tenantId,
            @Param("from")     LocalDateTime from);

    // ─── Global Admin : métriques toutes tenants confondues ──────────────────

    @Query("SELECT COUNT(er.id) FROM EvaluationRequest er WHERE er.requestedAt >= :from")
    Long countAllSince(@Param("from") LocalDateTime from);

    // Alertes : évaluations d'un ruleset dans une fenêtre glissante
    @Query("""
        SELECT COUNT(er.id)
        FROM EvaluationRequest er
        WHERE er.ruleSet.id  = :ruleSetId
          AND er.tenant.id   = :tenantId
          AND er.requestedAt >= :from
          AND er.requestedAt <  :to
    """)
    Long countByRuleSetAndTenantBetween(
            @Param("ruleSetId") Long ruleSetId,
            @Param("tenantId")  Long tenantId,
            @Param("from")      LocalDateTime from,
            @Param("to")        LocalDateTime to);
}
