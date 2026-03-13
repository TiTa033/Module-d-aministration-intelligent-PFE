package talan.pfe.rulengine.repositories;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private Tenant persistTenant() {
        Tenant tenant = Tenant.builder()
                .name("Tenant 1")
                .slug("tenant-" + UUID.randomUUID())
                .status(TenantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        return entityManager.persistAndFlush(tenant);
    }

    private User persistUser(Tenant tenant, String email, Role role) {
        User user = User.builder()
                .email(email)
                .passwordHash("pwd")
                .role(role)
                .tenant(tenant)
                .active(true)
                .build();
        return entityManager.persistAndFlush(user);
    }

    @Test
    @DisplayName("findByEmail doit retourner un utilisateur")
    void findByEmail_shouldReturnUser() {
        Tenant tenant = persistTenant();
        User user = persistUser(tenant, "user@test.com", Role.VIEWER);

        Optional<User> found = userRepository.findByEmail("user@test.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("existsByEmail doit retourner true si l'email existe")
    void existsByEmail_shouldReturnTrueWhenExists() {
        Tenant tenant = persistTenant();
        persistUser(tenant, "user@test.com", Role.VIEWER);

        boolean exists = userRepository.existsByEmail("user@test.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("findAllByTenantId doit retourner les utilisateurs du tenant")
    void findAllByTenantId_shouldReturnUsersOfTenant() {
        Tenant tenant1 = persistTenant();
        Tenant tenant2 = persistTenant();

        persistUser(tenant1, "u1@test.com", Role.VIEWER);
        persistUser(tenant1, "u2@test.com", Role.ADMIN);
        persistUser(tenant2, "other@test.com", Role.VIEWER);

        List<User> usersTenant1 = userRepository.findAllByTenantId(tenant1.getId());

        assertThat(usersTenant1).hasSize(2);
        assertThat(usersTenant1).extracting(User::getEmail)
                .containsExactlyInAnyOrder("u1@test.com", "u2@test.com");
    }

    @Test
    @DisplayName("existsByEmailAndTenantId doit distinguer les tenants")
    void existsByEmailAndTenantId_shouldBeTenantScoped() {
        Tenant tenant1 = persistTenant();
        Tenant tenant2 = persistTenant();

        persistUser(tenant1, "same@test.com", Role.VIEWER);

        boolean existsTenant1 =
                userRepository.existsByEmailAndTenantId("same@test.com", tenant1.getId());
        boolean existsTenant2 =
                userRepository.existsByEmailAndTenantId("same@test.com", tenant2.getId());

        assertThat(existsTenant1).isTrue();
        assertThat(existsTenant2).isFalse();
    }
}

