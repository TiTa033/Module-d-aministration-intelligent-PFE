package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.UserService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserController")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private UserResponse stubUser() {
        return UserResponse.builder()
                .id(1L)
                .name("Alice")
                .email("alice@bank.com")
                .role(Role.ADMIN)
                .tenantId(1L)
                .active(true)
                .build();
    }

    @Test @DisplayName("POST /api/tenants/{tenantId}/users → 201 CREATED")
    void create_returns201() throws Exception {
        when(userService.create(any(), eq(1L))).thenReturn(stubUser());

        CreateUserRequest req = new CreateUserRequest();
        req.setName("Alice");
        req.setEmail("alice@bank.com");
        req.setRole(Role.ADMIN);

        mockMvc.perform(post("/api/tenants/1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/tenants/{tenantId}/users → 200 OK")
    void getAll_returns200() throws Exception {
        when(userService.getByTenant(1L)).thenReturn(List.of(stubUser()));

        mockMvc.perform(get("/api/tenants/1/users"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/tenants/{tenantId}/users/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(userService.getById(1L, 1L)).thenReturn(stubUser());

        mockMvc.perform(get("/api/tenants/1/users/1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/tenants/{tenantId}/users/{id}/activate → 200 OK")
    void activate_returns200() throws Exception {
        when(userService.setActive(1L, 1L, true)).thenReturn(stubUser());

        mockMvc.perform(patch("/api/tenants/1/users/1/activate"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/tenants/{tenantId}/users/{id}/deactivate → 200 OK")
    void deactivate_returns200() throws Exception {
        UserResponse inactive = UserResponse.builder().id(1L).active(false).build();
        when(userService.setActive(1L, 1L, false)).thenReturn(inactive);

        mockMvc.perform(patch("/api/tenants/1/users/1/deactivate"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/tenants/{tenantId}/users/{id}/role → 200 OK")
    void changeRole_returns200() throws Exception {
        when(userService.changeRole(1L, 1L, Role.VIEWER)).thenReturn(stubUser());

        mockMvc.perform(patch("/api/tenants/1/users/1/role")
                        .param("role", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/tenants/{tenantId}/users/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        doNothing().when(userService).delete(1L, 1L);

        mockMvc.perform(delete("/api/tenants/1/users/1"))
                .andExpect(status().isNoContent());
    }
}
