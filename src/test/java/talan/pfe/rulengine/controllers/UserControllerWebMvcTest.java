package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.UserService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private UserResponse userResp(Long id, String email, Role role) {
        return UserResponse.builder()
                .id(id).email(email).role(role).active(true).build();
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_returns201WithCreatedUser() throws Exception {
        when(userService.create(any(), eq(5L))).thenReturn(userResp(1L, "u@acme.com", Role.VIEWER));

        mockMvc.perform(post("/api/tenants/5/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "u@acme.com", "name", "Alice", "role", "VIEWER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("u@acme.com"));
    }

    @Test
    void create_returns400WhenUserNotRegistered() throws Exception {
        when(userService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("doit deja etre inscrit"));

        mockMvc.perform(post("/api/tenants/5/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ghost@x.com", "name", "Ghost", "role", "VIEWER"))))
                .andExpect(status().isBadRequest());
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithUserList() throws Exception {
        when(userService.getByTenant(5L))
                .thenReturn(List.of(
                        userResp(1L, "a@acme.com", Role.ADMIN),
                        userResp(2L, "b@acme.com", Role.VIEWER)));

        mockMvc.perform(get("/api/tenants/5/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("a@acme.com"));
    }

    @Test
    void getAll_returns200WithEmptyList() throws Exception {
        when(userService.getByTenant(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/tenants/5/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200WithUser() throws Exception {
        when(userService.getById(1L, 5L)).thenReturn(userResp(1L, "a@acme.com", Role.ADMIN));

        mockMvc.perform(get("/api/tenants/5/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("a@acme.com"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(userService.getById(any(), any())).thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/api/tenants/5/users/999"))
                .andExpect(status().isNotFound());
    }

    // ─── ACTIVATE / DEACTIVATE ────────────────────────────────

    @Test
    void activate_returns200WithActiveUser() throws Exception {
        when(userService.setActive(1L, 5L, true)).thenReturn(userResp(1L, "a@acme.com", Role.VIEWER));

        mockMvc.perform(patch("/api/tenants/5/users/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deactivate_returns200WithInactiveUser() throws Exception {
        UserResponse resp = userResp(1L, "a@acme.com", Role.VIEWER);
        resp.setActive(false);
        when(userService.setActive(1L, 5L, false)).thenReturn(resp);

        mockMvc.perform(patch("/api/tenants/5/users/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ─── CHANGE ROLE ─────────────────────────────────────────

    @Test
    void changeRole_returns200WithUpdatedRole() throws Exception {
        when(userService.changeRole(1L, 5L, Role.MANAGER))
                .thenReturn(userResp(1L, "a@acme.com", Role.MANAGER));

        mockMvc.perform(patch("/api/tenants/5/users/1/role")
                        .param("role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_returns204OnSuccess() throws Exception {
        doNothing().when(userService).delete(1L, 5L);

        mockMvc.perform(delete("/api/tenants/5/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenUserNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("User not found"))
                .when(userService).delete(any(), any());

        mockMvc.perform(delete("/api/tenants/5/users/999"))
                .andExpect(status().isNotFound());
    }
}