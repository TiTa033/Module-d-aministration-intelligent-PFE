package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.NotificationResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.enums.NotifType;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.NotificationService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean NotificationService notificationService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private static final String AUTH = "Bearer t";

    private void stubTenant() {
        when(jwtService.extractTenantId("t")).thenReturn("10");
    }

    private PageResponse<NotificationResponse> pageOf(NotificationResponse... notifs) {
        return PageResponse.<NotificationResponse>builder()
                .content(List.of(notifs)).page(0).size(20)
                .totalElements(notifs.length).totalPages(1)
                .last(true).build();
    }

    private NotificationResponse notifResp(Long id, boolean read) {
        return NotificationResponse.builder()
                .id(id).title("Alert").message("Something happened")
                .type(NotifType.WARNING).read(read).tenantId(10L)
                .createdAt(LocalDateTime.now()).build();
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returns200WithNotifications() throws Exception {
        stubTenant();
        when(notificationService.getAll(eq(10L), any()))
                .thenReturn(pageOf(notifResp(1L, false), notifResp(2L, true)));

        mockMvc.perform(get("/api/notifications").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[1].read").value(true))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getAll_returnsEmptyPageWhenNoNotifications() throws Exception {
        stubTenant();
        when(notificationService.getAll(eq(10L), any())).thenReturn(pageOf());

        mockMvc.perform(get("/api/notifications").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void getAll_withPaginationParams() throws Exception {
        stubTenant();
        when(notificationService.getAll(eq(10L), any())).thenReturn(pageOf());

        mockMvc.perform(get("/api/notifications")
                        .param("page", "1").param("size", "5")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    // ─── UNREAD COUNT ─────────────────────────────────────────

    @Test
    void getUnreadCount_returns200WithCount() throws Exception {
        stubTenant();
        when(notificationService.getUnreadCount(10L)).thenReturn(7L);

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }

    @Test
    void getUnreadCount_returnsZeroWhenAllRead() throws Exception {
        stubTenant();
        when(notificationService.getUnreadCount(10L)).thenReturn(0L);

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // ─── MARK ALL AS READ ─────────────────────────────────────

    @Test
    void markAllAsRead_returns204() throws Exception {
        stubTenant();
        doNothing().when(notificationService).markAllAsRead(10L);

        mockMvc.perform(patch("/api/notifications/mark-all-read").header("Authorization", AUTH))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllAsRead(10L);
    }
}