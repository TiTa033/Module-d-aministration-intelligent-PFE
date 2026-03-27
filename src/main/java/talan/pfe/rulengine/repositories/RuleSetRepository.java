package talan.pfe.rulengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.RuleSetStatus;

import java.util.Optional;

@Repository
public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {

    boolean existsByNameAndTenantId(String name, Long tenantId);

    boolean existsByNameAndTenantIdAndIdNot(String name, Long tenantId, Long id);

    Optional<RuleSet> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT r FROM RuleSet r WHERE r.tenant.id = :tenantId " +
            "AND (:search = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR r.status = :status)")
    Page<RuleSet> findAllByTenantWithFilters(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("status") RuleSetStatus status,
            Pageable pageable
    );
}