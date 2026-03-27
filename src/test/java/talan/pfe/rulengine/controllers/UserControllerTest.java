package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.services.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class UserControllerTest {

    private UserService userService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        controller = new UserController(userService);
    }

    @Test
    void getAll_shouldReturnList() {
        when(userService.getByTenant(1L)).thenReturn(List.of(UserResponse.builder().id(10L).email("u@x.com").build()));
        var response = controller.getAll(1L);
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void create_shouldReturnCreated() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("u@x.com");
        req.setName("User");
        req.setRole(Role.VIEWER);
        when(userService.create(req, 1L)).thenReturn(UserResponse.builder().id(10L).email("u@x.com").build());
        var response = controller.create(1L, req);
        assertNotNull(response.getBody());
    }
}

