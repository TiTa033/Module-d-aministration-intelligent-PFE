package talan.pfe.rulengine.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.AuditService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuditController")
class AuditControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AuditService auditService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test @DisplayName("GET /api/audit → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(auditService.getAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.builder().content(List.of()).page(0).size(20)
                        .totalElements(0).totalPages(0).last(true).build());

        mockMvc.perform(get("/api/audit").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/audit with filters → 200 OK")
    void getAll_withFilters_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(auditService.getAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.builder().content(List.of()).page(0).size(20)
                        .totalElements(0).totalPages(0).last(true).build());

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer t")
                        .param("entityType", "RULESET")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }
}
