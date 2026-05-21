package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.TenantResponse;

import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.TenantService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TenantLookupController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantLookupControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TenantService tenantService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private TenantResponse tenantResp(Long id, String name) {
        return TenantResponse.builder()
                .id(id).name(name).slug(name.toLowerCase())
                .status("ACTIVE").build();
    }

    @Test
    void getById_returns200WithTenant() throws Exception {
        when(tenantService.getById(1L)).thenReturn(tenantResp(1L, "Acme"));

        mockMvc.perform(get("/api/tenants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getById_returns404WhenTenantNotFound() throws Exception {
        when(tenantService.getById(999L))
                .thenThrow(new ResourceNotFoundException("Tenant not found with id: 999"));

        mockMvc.perform(get("/api/tenants/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tenant not found with id: 999"));
    }

    @Test
    void getById_returns200WithInactiveTenant() throws Exception {
        TenantResponse resp = tenantResp(2L, "OldCorp");
        resp.setStatus("INACTIVE");
        when(tenantService.getById(2L)).thenReturn(resp);

        mockMvc.perform(get("/api/tenants/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }
}