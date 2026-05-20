package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.UserMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @Mock UserMapper userMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks UserServiceImpl service;

    private Tenant tenant;
    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").slug("bankcorp")
                .status(TenantStatus.ACTIVE).build();
        user = User.builder().id(5L).email("john@bank.com").name("John")
                .role(Role.VIEWER).tenant(tenant).active(true).build();
        userResponse = UserResponse.builder().id(5L).email("john@bank.com").build();
    }

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("should assign existing user to tenant")
        void create_success() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmail("john@bank.com")).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);
            when(userMapper.toDto(any())).thenReturn(userResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            CreateUserRequest req = new CreateUserRequest();
            req.setEmail("john@bank.com");
            req.setName("John");
            req.setRole(Role.VIEWER);

            UserResponse result = service.create(req, 1L);
            assertThat(result.getEmail()).isEqualTo("john@bank.com");
            verify(userRepository).save(user);
        }

        @Test @DisplayName("should throw IllegalArgumentException when user does not exist")
        void create_userNotRegistered_throwsIllegalArgument() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmail("unknown@bank.com")).thenReturn(Optional.empty());

            CreateUserRequest req = new CreateUserRequest();
            req.setEmail("unknown@bank.com");
            req.setRole(Role.VIEWER);

            assertThatThrownBy(() -> service.create(req, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("inscrit");
        }

        @Test @DisplayName("should throw IllegalArgumentException when user already belongs to another tenant")
        void create_userBelongsToOtherTenant_throwsIllegalArgument() {
            Tenant otherTenant = Tenant.builder().id(99L).name("OtherBank").build();
            user.setTenant(otherTenant);
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmail("john@bank.com")).thenReturn(Optional.of(user));

            CreateUserRequest req = new CreateUserRequest();
            req.setEmail("john@bank.com");
            req.setRole(Role.VIEWER);

            assertThatThrownBy(() -> service.create(req, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("autre tenant");
        }
    }

    @Nested @DisplayName("getByTenant()")
    class GetByTenant {

        @Test @DisplayName("should return all users for tenant")
        void getByTenant_returnsList() {
            when(userRepository.findAllByTenantId(1L)).thenReturn(List.of(user));
            when(userMapper.toDtoList(any())).thenReturn(List.of(userResponse));

            List<UserResponse> result = service.getByTenant(1L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("john@bank.com");
        }
    }

    @Nested @DisplayName("setActive()")
    class SetActive {

        @Test @DisplayName("should deactivate a user")
        void setActive_false_deactivatesUser() {
            when(userRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);
            when(userMapper.toDto(any())).thenReturn(userResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            service.setActive(5L, 1L, false);
            assertThat(user.isActive()).isFalse();
        }

        @Test @DisplayName("should activate a user")
        void setActive_true_activatesUser() {
            user.setActive(false);
            when(userRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);
            when(userMapper.toDto(any())).thenReturn(userResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            service.setActive(5L, 1L, true);
            assertThat(user.isActive()).isTrue();
        }
    }

    @Nested @DisplayName("changeRole()")
    class ChangeRole {

        @Test @DisplayName("should change user role and audit the change")
        void changeRole_success() {
            when(userRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);
            when(userMapper.toDto(any())).thenReturn(userResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            service.changeRole(5L, 1L, Role.ADMIN);
            assertThat(user.getRole()).isEqualTo(Role.ADMIN);
            verify(auditProducer).publish(eq(talan.pfe.rulengine.enums.AuditAction.USER_ROLE_CHANGED),
                    any(), any(), eq("VIEWER"), eq("ADMIN"), any(), any(), any());
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {

        @Test @DisplayName("should delete user from tenant")
        void delete_success() {
            when(userRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(user));
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(userRepository).delete(user);

            service.delete(5L, 1L);
            verify(userRepository).delete(user);
        }
    }
}
