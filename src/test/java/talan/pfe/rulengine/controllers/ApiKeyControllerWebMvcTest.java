package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.ApiKeyCreatedResponse;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.ApiKeyService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ApiKeyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiKeyControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ApiKeyService apiKeyService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("10");
    }

    private ApiKeyCreatedResponse createdResp(Long id) {
        return ApiKeyCreatedResponse.builder()
                .id(id).name("MyKey").rawKey("raas_abc123")
                .keyPrefix("raas_abc").tenantId(10L)
                .createdAt(LocalDateTime.now()).build();
    }

    private ApiKeyResponse keyResp(Long id, boolean active) {
        return ApiKeyResponse.builder()
                .id(id).name("MyKey").active(active)
                .keyPrefix("raas_abc").tenantId(10L).tenantName("Acme")
                .createdAt(LocalDateTime.now()).build();
    }

    // ─── GENERATE ────────────────────────────────────────────

    @Test
    void generate_returns201WithRawKey() throws Exception {
        stubTenant();
        when(apiKeyService.generate(any(), eq(10L))).thenReturn(createdResp(1L));

        mockMvc.perform(post("/api/apikeys")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "MyKey", "ruleSetId", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.rawKey").value("raas_abc123"))
                .andExpect(jsonPath("$.name").value("MyKey"));
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithList() throws Exception {
        stubTenant();
        when(apiKeyService.getAll(10L))
                .thenReturn(List.of(keyResp(1L, true), keyResp(2L, false)));

        mockMvc.perform(get("/api/apikeys")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void getAll_returns200WithEmptyListWhenNoKeys() throws Exception {
        stubTenant();
        when(apiKeyService.getAll(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/apikeys").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returns200() throws Exception {
        stubTenant();
        when(apiKeyService.getById(1L, 10L)).thenReturn(keyResp(1L, true));

        mockMvc.perform(get("/api/apikeys/1").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        stubTenant();
        when(apiKeyService.getById(any(), any()))
                .thenThrow(new ResourceNotFoundException("API key not found"));

        mockMvc.perform(get("/api/apikeys/999").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── REVOKE ──────────────────────────────────────────────

    @Test
    void revoke_returns200WithRevokedKey() throws Exception {
        stubTenant();
        when(apiKeyService.revoke(1L, 10L)).thenReturn(keyResp(1L, false));

        mockMvc.perform(patch("/api/apikeys/1/revoke").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void revoke_returns400WhenAlreadyRevoked() throws Exception {
        stubTenant();
        when(apiKeyService.revoke(any(), any()))
                .thenThrow(new BadRequestException("API key is already revoked"));

        mockMvc.perform(patch("/api/apikeys/1/revoke").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("API key is already revoked"));
    }

    // ─── REGENERATE ──────────────────────────────────────────

    @Test
    void regenerate_returns200WithNewRawKey() throws Exception {
        stubTenant();
        ApiKeyCreatedResponse newKey = ApiKeyCreatedResponse.builder()
                .id(2L).name("MyKey").rawKey("raas_newkey123")
                .keyPrefix("raas_new").tenantId(10L).build();
        when(apiKeyService.regenerate(1L, 10L)).thenReturn(newKey);

        mockMvc.perform(post("/api/apikeys/1/regenerate").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawKey").value("raas_newkey123"));
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_returns204OnSuccess() throws Exception {
        stubTenant();
        doNothing().when(apiKeyService).delete(1L, 10L);

        mockMvc.perform(delete("/api/apikeys/1").header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns400WhenKeyIsActive() throws Exception {
        stubTenant();
        doThrow(new BadRequestException("Cannot delete an active API key. Revoke it first."))
                .when(apiKeyService).delete(any(), any());

        mockMvc.perform(delete("/api/apikeys/1").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete an active API key. Revoke it first."));
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        stubTenant();
        doThrow(new ResourceNotFoundException("API key not found"))
                .when(apiKeyService).delete(any(), any());

        mockMvc.perform(delete("/api/apikeys/999").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }
}