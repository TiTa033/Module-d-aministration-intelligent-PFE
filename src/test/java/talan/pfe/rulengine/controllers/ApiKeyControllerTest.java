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
import talan.pfe.rulengine.dtos.response.ApiKeyCreatedResponse;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.ApiKeyService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ApiKeyController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ApiKeyController")
class ApiKeyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ApiKeyService apiKeyService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test @DisplayName("POST /api/apikeys → 201 CREATED")
    void generate_returns201() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(apiKeyService.generate(any(), eq(1L))).thenReturn(
                ApiKeyCreatedResponse.builder().id(1L).name("MyKey").rawKey("raas_abc").build());

        mockMvc.perform(post("/api/apikeys")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "MyKey", "ruleSetId", 10))))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("GET /api/apikeys → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(apiKeyService.getAll(1L)).thenReturn(List.of(ApiKeyResponse.builder().id(1L).build()));

        mockMvc.perform(get("/api/apikeys").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/apikeys/{id} → 200 OK")
    void getById_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(apiKeyService.getById(1L, 1L)).thenReturn(ApiKeyResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/apikeys/1").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /api/apikeys/{id}/revoke → 200 OK")
    void revoke_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(apiKeyService.revoke(1L, 1L)).thenReturn(ApiKeyResponse.builder().id(1L).active(false).build());

        mockMvc.perform(patch("/api/apikeys/1/revoke").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /api/apikeys/{id}/regenerate → 200 OK")
    void regenerate_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(apiKeyService.regenerate(1L, 1L)).thenReturn(
                ApiKeyCreatedResponse.builder().id(2L).rawKey("raas_new").build());

        mockMvc.perform(post("/api/apikeys/1/regenerate").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/apikeys/{id} → 204 NO CONTENT")
    void delete_returns204() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        doNothing().when(apiKeyService).delete(1L, 1L);

        mockMvc.perform(delete("/api/apikeys/1").header("Authorization", "Bearer t"))
                .andExpect(status().isNoContent());
    }
}
