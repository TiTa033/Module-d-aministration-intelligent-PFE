package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;

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
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

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
        createRequest.setEmail("user@test.com");
        createRequest.setPassword("password123");
        createRequest.setRole(Role.VIEWER);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("doit créer un utilisateur avec succès")
        void shouldCreateUserSuccessfully() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(userRepository.existsByEmailAndTenantId(createRequest.getEmail(), tenantId))
                    .thenReturn(false);
            when(passwordEncoder.encode(createRequest.getPassword()))
                    .thenReturn("encodedPassword");

            User savedUser = User.builder()
                    .id(UUID.randomUUID())
                    .email(createRequest.getEmail())
                    .passwordHash("encodedPassword")
                    .role(Role.VIEWER)
                    .tenant(tenant)
                    .active(true)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userService.create(createRequest, tenantId);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("user@test.com");
            assertThat(result.getRole()).isEqualTo(Role.VIEWER);
            assertThat(result.getTenant()).isEqualTo(tenant);
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
        @DisplayName("doit lever une exception si l'email existe déjà pour le tenant")
        void shouldThrowWhenEmailAlreadyExists() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(userRepository.existsByEmailAndTenantId(createRequest.getEmail(), tenantId))
                    .thenReturn(true);

            assertThatThrownBy(() -> userService.create(createRequest, tenantId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already used for this tenant");
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
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            User result = userService.getById(userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getEmail()).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("doit lever une exception si l'utilisateur n'existe pas")
        void shouldThrowWhenUserNotFound() {
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(userId))
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
            doNothing().when(userRepository).deleteById(userId);

            userService.delete(userId);

            verify(userRepository).deleteById(userId);
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
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.setActive(userId, true);

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
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.setActive(userId, false);

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
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.changeRole(userId, Role.ADMIN);

            assertThat(result.getRole()).isEqualTo(Role.ADMIN);
            verify(userRepository).save(user);
        }
    }
}

