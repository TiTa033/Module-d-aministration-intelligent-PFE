package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.AuditLogResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.AuditService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuditService auditService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("10");
    }

    private PageResponse<AuditLogResponse> pageOf(AuditLogResponse... logs) {
        return PageResponse.<AuditLogResponse>builder()
                .content(List.of(logs)).page(0).size(20)
                .totalElements(logs.length).totalPages(1)
                .last(true).build();
    }

    private AuditLogResponse logResp(Long id, AuditAction action, String entityType) {
        return AuditLogResponse.builder()
                .id(id).action(action).entityType(entityType).entityId(id)
                .timestamp(LocalDateTime.now()).tenantId(10L).build();
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithAuditLogs() throws Exception {
        stubTenant();
        when(auditService.getAll(eq(10L), any(), any(), any(), any(), any()))
                .thenReturn(pageOf(logResp(1L, AuditAction.RULE_CREATED, "RULE")));

        mockMvc.perform(get("/api/audit").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].entityType").value("RULE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAll_withActionFilter_returns200() throws Exception {
        stubTenant();
        when(auditService.getAll(eq(10L), eq(AuditAction.RULE_CREATED), any(), any(), any(), any()))
                .thenReturn(pageOf(logResp(1L, AuditAction.RULE_CREATED, "RULE")));

        mockMvc.perform(get("/api/audit")
                        .param("action", "RULE_CREATED")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("RULE_CREATED"));
    }

    @Test
    void getAll_withEntityTypeFilter_returns200() throws Exception {
        stubTenant();
        when(auditService.getAll(eq(10L), any(), eq("RULESET"), any(), any(), any()))
                .thenReturn(pageOf(logResp(2L, AuditAction.RULESET_CREATED, "RULESET")));

        mockMvc.perform(get("/api/audit")
                        .param("entityType", "RULESET")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entityType").value("RULESET"));
    }

    @Test
    void getAll_returnsEmptyPageWhenNoLogs() throws Exception {
        stubTenant();
        when(auditService.getAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(pageOf());

        mockMvc.perform(get("/api/audit").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getAll_withPaginationParams_returns200() throws Exception {
        stubTenant();
        when(auditService.getAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(pageOf(logResp(1L, AuditAction.RULE_UPDATED, "RULE")));

        mockMvc.perform(get("/api/audit")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }
}