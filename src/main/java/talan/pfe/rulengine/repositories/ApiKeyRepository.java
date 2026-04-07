package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.ApiKey;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<ApiKey> findByIdAndTenantId(Long id, Long tenantId);

    // Used by evaluation engine to find active keys
    List<ApiKey> findAllByTenantIdAndActiveTrue(Long tenantId);

    List<ApiKey> findAllByKeyPrefixAndActiveTrue(String keyPrefix);
}