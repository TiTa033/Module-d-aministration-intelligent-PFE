package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findAllByTenantId(Long tenantId);
    List<User> findAllByTenantIdAndRole(Long tenantId, Role role);
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByEmailAndTenantId(String email, Long tenantId);
}