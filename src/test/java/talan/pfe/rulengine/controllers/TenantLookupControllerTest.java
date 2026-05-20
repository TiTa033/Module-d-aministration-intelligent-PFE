package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.TenantService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TenantLookupController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TenantLookupController")
class TenantLookupControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TenantService tenantService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test @DisplayName("GET /api/tenants/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(tenantService.getById(1L)).thenReturn(
                TenantResponse.builder().id(1L).name("BankCorp").slug("bankcorp").status("ACTIVE").build());

        mockMvc.perform(get("/api/tenants/1"))
                .andExpect(status().isOk());
    }
}
