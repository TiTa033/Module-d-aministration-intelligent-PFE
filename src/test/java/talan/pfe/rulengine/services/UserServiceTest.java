package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.RefreshTokenRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.services.serviceImpl.UserServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID tenantId;
    private Tenant tenant;
    private CreateUserRequest createRequest;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(TenantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        createRequest = new CreateUserRequest();
        createRequest.setName("Test User");
        createRequest.setEmail("user@test.com");
        createRequest.setRole(Role.VIEWER);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("doit donner l acces a un utilisateur deja inscrit")
        void shouldGrantAccessToExistingUser() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            User existingUser = User.builder()
                    .id(UUID.randomUUID())
                    .name("Old Name")
                    .email(createRequest.getEmail())
                    .passwordHash("existingPasswordHash")
                    .role(Role.VIEWER)
                    .tenant(tenant)
                    .active(false)
                    .build();
            when(userRepository.findByEmail(createRequest.getEmail()))
                    .thenReturn(Optional.of(existingUser));

            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.create(createRequest, tenantId);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Test User");
            assertThat(result.getEmail()).isEqualTo("user@test.com");
            assertThat(result.getRole()).isEqualTo(Role.VIEWER);
            assertThat(result.getTenant()).isEqualTo(tenant);
            assertThat(result.getPasswordHash()).isEqualTo("existingPasswordHash");
            assertThat(result.isActive()).isTrue();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("doit lever une exception si le tenant n'existe pas")
        void shouldThrowWhenTenantNotFound() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.create(createRequest, tenantId))
                    .isInstanceOf(NoSuchElementException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("doit lever une exception si l utilisateur n est pas encore inscrit")
        void shouldThrowWhenUserIsNotRegistered() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmail(createRequest.getEmail()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.create(createRequest, tenantId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cet utilisateur doit deja etre inscrit avant de recevoir un acces");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("doit lever une exception si l utilisateur appartient a un autre tenant")
        void shouldThrowWhenUserBelongsToAnotherTenant() {
            UUID anotherTenantId = UUID.randomUUID();
            Tenant anotherTenant = Tenant.builder()
                    .id(anotherTenantId)
                    .name("Another Tenant")
                    .slug("another-tenant")
                    .status(TenantStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmail(createRequest.getEmail()))
                    .thenReturn(Optional.of(User.builder()
                            .id(UUID.randomUUID())
                            .email(createRequest.getEmail())
                            .passwordHash("existingPasswordHash")
                            .role(Role.VIEWER)
                            .tenant(anotherTenant)
                            .active(true)
                            .build()));

            assertThatThrownBy(() -> userService.create(createRequest, tenantId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cet utilisateur est deja rattache a un autre tenant");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("doit retourner l'utilisateur par id")
        void shouldReturnUserById() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("user@test.com")
                    .role(Role.ADMIN)
                    .tenant(tenant)
                    .build();
            when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

            User result = userService.getById(userId, tenantId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getEmail()).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("doit lever une exception si l'utilisateur n'existe pas")
        void shouldThrowWhenUserNotFound() {
            UUID userId = UUID.randomUUID();
            when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(userId, tenantId))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("getByTenant")
    class GetByTenant {

        @Test
        @DisplayName("doit retourner la liste des utilisateurs du tenant")
        void shouldReturnUsersByTenant() {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("user@test.com")
                    .role(Role.VIEWER)
                    .tenant(tenant)
                    .build();
            when(userRepository.findAllByTenantId(tenantId))
                    .thenReturn(List.of(user));

            List<User> result = userService.getByTenant(tenantId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("doit retourner une liste vide si aucun utilisateur")
        void shouldReturnEmptyListWhenNoUsers() {
            when(userRepository.findAllByTenantId(tenantId))
                    .thenReturn(List.of());

            List<User> result = userService.getByTenant(tenantId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("doit supprimer l'utilisateur")
        void shouldDeleteUser() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("user@test.com")
                    .role(Role.VIEWER)
                    .tenant(tenant)
                    .build();
            when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

            userService.delete(userId, tenantId);

            verify(refreshTokenRepository).deleteByUser(user);
            verify(userRepository).delete(user);
        }
    }

    @Nested
    @DisplayName("setActive")
    class SetActive {

        @Test
        @DisplayName("doit activer un utilisateur")
        void shouldActivateUser() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("user@test.com")
                    .role(Role.VIEWER)
                    .tenant(tenant)
                    .active(false)
                    .build();
            when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.setActive(userId, tenantId, true);

            assertThat(result.isActive()).isTrue();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("doit désactiver un utilisateur")
        void shouldDeactivateUser() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("user@test.com")
                    .role(Role.VIEWER)
                    .tenant(tenant)
                    .active(true)
                    .build();
            when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.setActive(userId, tenantId, false);

            assertThat(result.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("changeRole")
    class ChangeRole {

        @Test
        @DisplayName("doit changer le rôle d'un utilisateur")
        void shouldChangeUserRole() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("user@test.com")
                    .role(Role.VIEWER)
                    .tenant(tenant)
                    .build();
            when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.changeRole(userId, tenantId, Role.ADMIN);

            assertThat(result.getRole()).isEqualTo(Role.ADMIN);
            verify(userRepository).save(user);
        }
    }
}

