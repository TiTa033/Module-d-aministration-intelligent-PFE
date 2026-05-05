package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.EvaluationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

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
}