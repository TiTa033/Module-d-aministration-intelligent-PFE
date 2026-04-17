package talan.pfe.rulengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    long countByTenantIdAndReadFalse(Long tenantId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.tenant.id = :tenantId AND n.read = false")
    void markAllAsRead(@Param("tenantId") Long tenantId);
}