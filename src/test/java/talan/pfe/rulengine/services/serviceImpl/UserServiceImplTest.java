package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.UserMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @Mock UserMapper userMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks UserServiceImpl service;

    private Tenant tenant(Long id) {
        return Tenant.builder().id(id).name("Acme").slug("acme").build();
    }

    private User user(Long id, Long tenantId, Role role) {
        return User.builder().id(id).email("u@acme.com").role(role)
                .tenant(tenant(tenantId)).active(true).build();
    }

    private UserResponse userResp(Long id, String email) {
        return UserResponse.builder().id(id).email(email).build();
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_assignsUserToTenantAndPublishesAudit() {
        Tenant t = tenant(5L);
        User existing = User.builder().id(1L).email("u@acme.com").build();
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("u@acme.com");
        req.setName("Alice");
        req.setRole(Role.VIEWER);

        when(tenantRepository.findById(5L)).thenReturn(Optional.of(t));
        when(userRepository.findByEmail("u@acme.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(userMapper.toDto(existing)).thenReturn(userResp(1L, "u@acme.com"));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        UserResponse result = service.create(req, 5L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(existing.getTenant()).isEqualTo(t);
        assertThat(existing.getRole()).isEqualTo(Role.VIEWER);
        assertThat(existing.isActive()).isTrue();
        verify(auditProducer).publish(eq(AuditAction.USER_CREATED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_throwsWhenUserNotRegistered() {
        Tenant t = tenant(5L);
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("ghost@acme.com");
        req.setRole(Role.VIEWER);

        when(tenantRepository.findById(5L)).thenReturn(Optional.of(t));
        when(userRepository.findByEmail("ghost@acme.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doit deja etre inscrit");
    }

    @Test
    void create_throwsWhenUserBelongsToAnotherTenant() {
        Tenant t = tenant(5L);
        Tenant otherTenant = tenant(99L);
        User existing = User.builder().id(1L).email("u@acme.com").tenant(otherTenant).build();
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("u@acme.com");
        req.setRole(Role.VIEWER);

        when(tenantRepository.findById(5L)).thenReturn(Optional.of(t));
        when(userRepository.findByEmail("u@acme.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(req, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autre tenant");
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returnsUserWhenFound() {
        User u = user(1L, 5L, Role.ADMIN);
        when(userRepository.findByIdAndTenantId(1L, 5L)).thenReturn(Optional.of(u));
        when(userMapper.toDto(u)).thenReturn(userResp(1L, "u@acme.com"));

        UserResponse result = service.getById(1L, 5L);
        assertThat(result.getId()).isEqualTo(1L);
    }

    // ─── GET BY TENANT ────────────────────────────────────────

    @Test
    void getByTenant_returnsAllUsersForTenant() {
        User u1 = user(1L, 5L, Role.ADMIN);
        User u2 = user(2L, 5L, Role.VIEWER);

        when(userRepository.findAllByTenantId(5L)).thenReturn(List.of(u1, u2));
        when(userMapper.toDtoList(List.of(u1, u2)))
                .thenReturn(List.of(userResp(1L, "a@acme.com"), userResp(2L, "b@acme.com")));

        List<UserResponse> result = service.getByTenant(5L);
        assertThat(result).hasSize(2);
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_removesUserAndPublishesAudit() {
        User u = user(1L, 5L, Role.VIEWER);
        when(userRepository.findByIdAndTenantId(1L, 5L)).thenReturn(Optional.of(u));
        doNothing().when(userRepository).delete(u);
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.delete(1L, 5L);

        verify(userRepository).delete(u);
        verify(auditProducer).publish(eq(AuditAction.USER_DELETED), any(), any(), any(), any(), any(), any(), any());
    }

    // ─── SET ACTIVE ──────────────────────────────────────────

    @Test
    void setActive_toTrue_setsUserActiveAndPublishesActivatedAudit() {
        User u = user(1L, 5L, Role.VIEWER);
        u.setActive(false);
        when(userRepository.findByIdAndTenantId(1L, 5L)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);
        when(userMapper.toDto(u)).thenReturn(userResp(1L, "u@acme.com"));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.setActive(1L, 5L, true);

        assertThat(u.isActive()).isTrue();
        verify(auditProducer).publish(eq(AuditAction.USER_ACTIVATED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void setActive_toFalse_deactivatesAndPublishesDeactivatedAudit() {
        User u = user(1L, 5L, Role.VIEWER);
        when(userRepository.findByIdAndTenantId(1L, 5L)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);
        when(userMapper.toDto(u)).thenReturn(userResp(1L, "u@acme.com"));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.setActive(1L, 5L, false);

        assertThat(u.isActive()).isFalse();
        verify(auditProducer).publish(eq(AuditAction.USER_DEACTIVATED), any(), any(), any(), any(), any(), any(), any());
    }

    // ─── CHANGE ROLE ─────────────────────────────────────────

    @Test
    void changeRole_updatesRoleAndPublishesAudit() {
        User u = user(1L, 5L, Role.VIEWER);
        when(userRepository.findByIdAndTenantId(1L, 5L)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);
        when(userMapper.toDto(u)).thenReturn(userResp(1L, "u@acme.com"));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.changeRole(1L, 5L, Role.ADMIN);

        assertThat(u.getRole()).isEqualTo(Role.ADMIN);
        verify(auditProducer).publish(eq(AuditAction.USER_ROLE_CHANGED), any(), any(),
                eq("VIEWER"), eq("ADMIN"), any(), any(), any());
    }
}