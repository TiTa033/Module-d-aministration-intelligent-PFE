package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.TenantResponse;

import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.TenantService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TenantController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TenantService tenantService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private TenantResponse tenantResp(Long id, String name, String slug) {
        return TenantResponse.builder()
                .id(id).name(name).slug(slug).status("ACTIVE").build();
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_validRequest_returns201() throws Exception {
        when(tenantService.create(any())).thenReturn(tenantResp(1L, "Acme", "acme"));

        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Acme", "slug", "acme"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.slug").value("acme"));
    }

    @Test
    void create_whenSlugConflict_returns409() throws Exception {
        when(tenantService.create(any())).thenThrow(new ConflictException("Slug 'acme' is already taken"));

        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Acme2", "slug", "acme"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Slug 'acme' is already taken"));
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200WithTenantDetails() throws Exception {
        TenantResponse resp = tenantResp(1L, "Acme", "acme");
        resp.setTotalUsers(5L);
        when(tenantService.getById(1L)).thenReturn(resp);

        mockMvc.perform(get("/api/admin/tenants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.totalUsers").value(5));
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        when(tenantService.getById(999L)).thenThrow(new ResourceNotFoundException("Tenant not found with id: 999"));

        mockMvc.perform(get("/api/admin/tenants/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tenant not found with id: 999"));
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithPaginatedContent() throws Exception {
        PageResponse<TenantResponse> page = PageResponse.<TenantResponse>builder()
                .content(List.of(tenantResp(1L, "Acme", "acme")))
                .page(0).size(10).totalElements(1).totalPages(1)
                .last(true).build();
        when(tenantService.getAll(any(), any(), anyInt(), anyInt(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Acme"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAll_withInvalidStatus_returns400() throws Exception {
        when(tenantService.getAll(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new BadRequestException("Invalid status. Must be ACTIVE or INACTIVE"));

        mockMvc.perform(get("/api/admin/tenants").param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status. Must be ACTIVE or INACTIVE"));
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_returns200() throws Exception {
        when(tenantService.update(eq(1L), any()))
                .thenReturn(tenantResp(1L, "NewName", "acme"));

        mockMvc.perform(put("/api/admin/tenants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "NewName"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"));
    }

    @Test
    void update_whenNotFound_returns404() throws Exception {
        when(tenantService.update(any(), any()))
                .thenThrow(new ResourceNotFoundException("Tenant not found"));

        mockMvc.perform(put("/api/admin/tenants/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "XY"))))
                .andExpect(status().isNotFound());
    }

    // ─── ACTIVATE / DEACTIVATE ────────────────────────────────

    @Test
    void activate_returns200() throws Exception {
        when(tenantService.activate(1L)).thenReturn(tenantResp(1L, "Acme", "acme"));

        mockMvc.perform(patch("/api/admin/tenants/1/activate"))
                .andExpect(status().isOk());
    }

    @Test
    void activate_whenAlreadyActive_returns400() throws Exception {
        when(tenantService.activate(1L))
                .thenThrow(new BadRequestException("Tenant is already active"));

        mockMvc.perform(patch("/api/admin/tenants/1/activate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tenant is already active"));
    }

    @Test
    void deactivate_returns200() throws Exception {
        TenantResponse resp = tenantResp(1L, "Acme", "acme");
        resp.setStatus("INACTIVE");
        when(tenantService.deactivate(1L)).thenReturn(resp);

        mockMvc.perform(patch("/api/admin/tenants/1/deactivate"))
                .andExpect(status().isOk());
    }

    @Test
    void deactivate_whenAlreadyInactive_returns400() throws Exception {
        when(tenantService.deactivate(1L))
                .thenThrow(new BadRequestException("Tenant is already inactive"));

        mockMvc.perform(patch("/api/admin/tenants/1/deactivate"))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(tenantService).delete(1L);

        mockMvc.perform(delete("/api/admin/tenants/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenHasUsers_returns400() throws Exception {
        doThrow(new BadRequestException("Cannot delete tenant with 3 active users"))
                .when(tenantService).delete(1L);

        mockMvc.perform(delete("/api/admin/tenants/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete tenant with 3 active users"));
    }

    @Test
    void delete_whenNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Tenant not found"))
                .when(tenantService).delete(999L);

        mockMvc.perform(delete("/api/admin/tenants/999"))
                .andExpect(status().isNotFound());
    }
}