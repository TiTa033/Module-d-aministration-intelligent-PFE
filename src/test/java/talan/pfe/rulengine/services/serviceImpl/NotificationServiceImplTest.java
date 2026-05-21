package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import talan.pfe.rulengine.dtos.response.NotificationResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.entites.Notification;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.NotifType;
import talan.pfe.rulengine.repositories.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock NotificationRepository notificationRepository;

    @InjectMocks NotificationServiceImpl service;

    private Notification notification(Long id, Long tenantId, boolean read) {
        return Notification.builder()
                .id(id).title("Alert").message("Something happened")
                .type(NotifType.WARNING).read(read).entityId(1L).entityType("RULE")
                .tenant(Tenant.builder().id(tenantId).build())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returnsMappedPageResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        Notification n1 = notification(1L, 10L, false);
        Notification n2 = notification(2L, 10L, true);

        when(notificationRepository.findByTenantIdOrderByCreatedAtDesc(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(n1, n2), pageable, 2));

        PageResponse<NotificationResponse> result = service.getAll(10L, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).isRead()).isFalse();
        assertThat(result.getContent().get(1).isRead()).isTrue();
    }

    @Test
    void getAll_mapsAllFieldsCorrectly() {
        Pageable pageable = PageRequest.of(0, 20);
        Notification n = notification(1L, 10L, false);

        when(notificationRepository.findByTenantIdOrderByCreatedAtDesc(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(n), pageable, 1));

        PageResponse<NotificationResponse> result = service.getAll(10L, pageable);
        NotificationResponse resp = result.getContent().get(0);

        assertThat(resp.getTitle()).isEqualTo("Alert");
        assertThat(resp.getMessage()).isEqualTo("Something happened");
        assertThat(resp.getType()).isEqualTo(NotifType.WARNING);
        assertThat(resp.getEntityType()).isEqualTo("RULE");
        assertThat(resp.getTenantId()).isEqualTo(10L);
    }

    @Test
    void getAll_returnsEmptyPageWhenNoNotifications() {
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByTenantIdOrderByCreatedAtDesc(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<NotificationResponse> result = service.getAll(10L, pageable);
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ─── GET UNREAD COUNT ─────────────────────────────────────

    @Test
    void getUnreadCount_returnsDelegatedCount() {
        when(notificationRepository.countByTenantIdAndReadFalse(10L)).thenReturn(5L);

        long count = service.getUnreadCount(10L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void getUnreadCount_returnsZeroWhenNoUnread() {
        when(notificationRepository.countByTenantIdAndReadFalse(10L)).thenReturn(0L);

        long count = service.getUnreadCount(10L);
        assertThat(count).isZero();
    }

    // ─── MARK ALL AS READ ─────────────────────────────────────

    @Test
    void markAllAsRead_callsRepositoryMethod() {
        doNothing().when(notificationRepository).markAllAsRead(10L);

        service.markAllAsRead(10L);

        verify(notificationRepository).markAllAsRead(10L);
    }
}