package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findAllByTenantId(UUID tenantId);
    List<User> findAllByTenantIdAndRole(UUID tenantId, Role role);
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);
}