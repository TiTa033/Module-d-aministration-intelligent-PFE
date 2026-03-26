package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.mappers.UserMapper;
import talan.pfe.rulengine.services.serviceImpl.UserServiceImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserServiceImpl userServiceImpl;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("create doit retourner 201 et UserResponse")
    void create_shouldReturnCreatedUser() {
        UUID tenantId = UUID.randomUUID();
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Test User");
        request.setEmail("user@test.com");
        request.setRole(Role.VIEWER);

        User user = User.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .email(request.getEmail())
                .role(Role.VIEWER)
                .build();

        UserResponse dto = UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(tenantId)
                .tenantName("Test Tenant")
                .active(true)
                .build();

        when(userServiceImpl.create(any(CreateUserRequest.class), any(UUID.class))).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        ResponseEntity<UserResponse> response = userController.create(tenantId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(dto);
        verify(userServiceImpl).create(request, tenantId);
        verify(userMapper).toDto(user);
    }

    @Test
    @DisplayName("getAll doit retourner la liste des utilisateurs du tenant")
    void getAll_shouldReturnUsersOfTenant() {
        UUID tenantId = UUID.randomUUID();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .role(Role.VIEWER)
                .build();

        UserResponse dto = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(tenantId)
                .active(true)
                .build();

        when(userServiceImpl.getByTenant(tenantId)).thenReturn(List.of(user));
        when(userMapper.toDtoList(List.of(user))).thenReturn(List.of(dto));

        ResponseEntity<List<UserResponse>> response = userController.getAll(tenantId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(dto);
        verify(userServiceImpl).getByTenant(tenantId);
        verify(userMapper).toDtoList(List.of(user));
    }

    @Test
    @DisplayName("getById doit retourner un utilisateur")
    void getById_shouldReturnUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@test.com")
                .role(Role.VIEWER)
                .build();

        UserResponse dto = UserResponse.builder()
                .id(userId)
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(tenantId)
                .active(true)
                .build();

        when(userServiceImpl.getById(userId, tenantId)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        ResponseEntity<UserResponse> response = userController.getById(tenantId, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
        verify(userServiceImpl).getById(userId, tenantId);
    }

    @Test
    @DisplayName("activate doit activer un utilisateur")
    void activate_shouldActivateUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@test.com")
                .role(Role.VIEWER)
                .active(true)
                .build();

        UserResponse dto = UserResponse.builder()
                .id(userId)
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(tenantId)
                .active(true)
                .build();

        when(userServiceImpl.setActive(userId, tenantId, true)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        ResponseEntity<UserResponse> response = userController.activate(tenantId, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isActive()).isTrue();
        verify(userServiceImpl).setActive(userId, tenantId, true);
    }

    @Test
    @DisplayName("deactivate doit désactiver un utilisateur")
    void deactivate_shouldDeactivateUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@test.com")
                .role(Role.VIEWER)
                .active(false)
                .build();

        UserResponse dto = UserResponse.builder()
                .id(userId)
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(tenantId)
                .active(false)
                .build();

        when(userServiceImpl.setActive(userId, tenantId, false)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        ResponseEntity<UserResponse> response = userController.deactivate(tenantId, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isActive()).isFalse();
        verify(userServiceImpl).setActive(userId, tenantId, false);
    }

    @Test
    @DisplayName("changeRole doit changer le rôle de l'utilisateur")
    void changeRole_shouldChangeUserRole() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User updatedUser = User.builder()
                .id(userId)
                .email("user@test.com")
                .role(Role.ADMIN)
                .active(true)
                .build();

        UserResponse dto = UserResponse.builder()
                .id(userId)
                .email(updatedUser.getEmail())
                .role(updatedUser.getRole())
                .tenantId(tenantId)
                .active(true)
                .build();

        when(userServiceImpl.changeRole(userId, tenantId, Role.ADMIN)).thenReturn(updatedUser);
        when(userMapper.toDto(updatedUser)).thenReturn(dto);

        ResponseEntity<UserResponse> response =
                userController.changeRole(tenantId, userId, Role.ADMIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRole()).isEqualTo(Role.ADMIN);
        verify(userServiceImpl).changeRole(userId, tenantId, Role.ADMIN);
    }

    @Test
    @DisplayName("delete doit retourner 204 NO_CONTENT")
    void delete_shouldReturnNoContent() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        doNothing().when(userServiceImpl).delete(userId, tenantId);

        ResponseEntity<Void> response = userController.delete(tenantId, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userServiceImpl).delete(userId, tenantId);
    }
}

