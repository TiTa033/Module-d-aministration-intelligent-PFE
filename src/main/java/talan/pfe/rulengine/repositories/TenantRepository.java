package talan.pfe.rulengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.TenantStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    // Search by name or slug with optional status filter
    @Query("SELECT t FROM Tenant t WHERE " +
            "(:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR t.status = :status)")
    Page<Tenant> findAllWithFilters(
            @Param("search") String search,
            @Param("status") TenantStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.id = :tenantId")
    long countUsersByTenantId(@Param("tenantId") UUID tenantId);
}