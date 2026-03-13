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
import java.util.UUID;

@Repository
public interface RuleSetRepository extends JpaRepository<RuleSet, UUID> {

    boolean existsByNameAndTenantId(String name, UUID tenantId);

    boolean existsByNameAndTenantIdAndIdNot(String name, UUID tenantId, UUID id);

    Optional<RuleSet> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT r FROM RuleSet r WHERE r.tenant.id = :tenantId " +
            "AND (:search = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR r.status = :status)")
    Page<RuleSet> findAllByTenantWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("status") RuleSetStatus status,
            Pageable pageable
    );
}