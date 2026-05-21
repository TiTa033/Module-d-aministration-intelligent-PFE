package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    @Query("SELECT a FROM AuditLog a LEFT JOIN FETCH a.user WHERE a.tenant.id = :tenantId AND a.timestamp >= :since")
    List<AuditLog> findByTenantIdAndTimestampAfter(@Param("tenantId") Long tenantId,
                                                    @Param("since") LocalDateTime since);
}