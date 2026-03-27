package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.mappers.UserMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.services.serviceImpl.UserServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserMapper userMapper;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, tenantRepository, userMapper);
    }

    @Test
    void create_shouldReturnUserResponse() {
        Tenant tenant = Tenant.builder().id(1L).name("T1").slug("t1").build();
        User user = User.builder().id(10L).email("u@x.com").tenant(tenant).role(Role.VIEWER).build();
        UserResponse dto = UserResponse.builder().id(10L).email("u@x.com").tenantId(1L).build();

        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("u@x.com");
        req.setName("User");
        req.setRole(Role.VIEWER);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        assertNotNull(service.create(req, 1L));
    }
}

