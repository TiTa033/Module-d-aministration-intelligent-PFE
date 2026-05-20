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
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.TenantService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TenantController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TenantController")
class TenantControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TenantService tenantService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    private TenantResponse stub() {
        return TenantResponse.builder().id(1L).name("BankCorp").slug("bankcorp").status("ACTIVE").build();
    }

    @Test @DisplayName("POST /api/admin/tenants → 201 CREATED")
    void create_returns201() throws Exception {
        when(tenantService.create(any())).thenReturn(stub());

        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "BankCorp", "slug", "bankcorp"))))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/admin/tenants/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(tenantService.getById(1L)).thenReturn(stub());

        mockMvc.perform(get("/api/admin/tenants/1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/admin/tenants → 200 OK")
    void getAll_returns200() throws Exception {
        PageResponse<TenantResponse> page = PageResponse.<TenantResponse>builder()
                .content(List.of(stub())).page(0).size(10).totalElements(1).totalPages(1).last(true).build();
        when(tenantService.getAll(any(), any(), anyInt(), anyInt(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/tenants"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PUT /api/admin/tenants/{id} → 200 OK")
    void update_returns200() throws Exception {
        when(tenantService.update(eq(1L), any())).thenReturn(stub());

        mockMvc.perform(put("/api/admin/tenants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "NewName"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/admin/tenants/{id}/activate → 200 OK")
    void activate_returns200() throws Exception {
        when(tenantService.activate(1L)).thenReturn(stub());

        mockMvc.perform(patch("/api/admin/tenants/1/activate"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/admin/tenants/{id}/deactivate → 200 OK")
    void deactivate_returns200() throws Exception {
        when(tenantService.deactivate(1L)).thenReturn(stub());

        mockMvc.perform(patch("/api/admin/tenants/1/deactivate"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/admin/tenants/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        doNothing().when(tenantService).delete(1L);

        mockMvc.perform(delete("/api/admin/tenants/1"))
                .andExpect(status().isNoContent());
    }
}
