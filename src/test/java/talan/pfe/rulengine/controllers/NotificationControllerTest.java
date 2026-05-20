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
import talan.pfe.rulengine.services.NotificationService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("NotificationController")
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean NotificationService notificationService;
    @MockBean JwtService jwtService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test @DisplayName("GET /api/notifications → 200 OK")
    void getAll_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(notificationService.getAll(eq(1L), any()))
                .thenReturn(PageResponse.builder().content(List.of()).page(0).size(20)
                        .totalElements(0).totalPages(0).last(true).build());

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/notifications/unread-count → 200 OK")
    void getUnreadCount_returns200() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        when(notificationService.getUnreadCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.count").value(3));
    }

    @Test @DisplayName("PATCH /api/notifications/mark-all-read → 204 NO CONTENT")
    void markAllRead_returns204() throws Exception {
        when(jwtService.extractTenantId("t")).thenReturn("1");
        doNothing().when(notificationService).markAllAsRead(1L);

        mockMvc.perform(patch("/api/notifications/mark-all-read").header("Authorization", "Bearer t"))
                .andExpect(status().isNoContent());
    }
}
