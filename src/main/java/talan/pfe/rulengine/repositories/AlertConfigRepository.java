package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.AlertConfig;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertConfigRepository extends JpaRepository<AlertConfig, Long> {

    List<AlertConfig> findAllByTenantIdAndEnabledTrue(Long tenantId);

    List<AlertConfig> findAllByEnabledTrue();

    Optional<AlertConfig> findByIdAndTenantId(Long id, Long tenantId);

    List<AlertConfig> findAllByTenantId(Long tenantId);
}
