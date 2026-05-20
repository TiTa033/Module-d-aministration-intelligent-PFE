package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationServiceImpl service;

    private Tenant tenant;
    private Notification notification;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        notification = Notification.builder()
                .id(1L).title("RuleSet activé").message("RS actif")
                .type(NotifType.SUCCESS).read(false)
                .tenant(tenant).createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("getAll() should return paginated notifications for tenant")
    void getAll_returnsPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByTenantIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(notification)));

        PageResponse<NotificationResponse> result = service.getAll(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("RuleSet activé");
        assertThat(result.getContent().get(0).isRead()).isFalse();
    }

    @Test
    @DisplayName("getAll() should return empty page when no notifications exist")
    void getAll_emptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByTenantIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<NotificationResponse> result = service.getAll(1L, pageable);
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("getUnreadCount() should return number of unread notifications")
    void getUnreadCount_returnsCorrectCount() {
        when(notificationRepository.countByTenantIdAndReadFalse(1L)).thenReturn(7L);
        assertThat(service.getUnreadCount(1L)).isEqualTo(7L);
    }

    @Test
    @DisplayName("getUnreadCount() should return 0 when all notifications are read")
    void getUnreadCount_zero() {
        when(notificationRepository.countByTenantIdAndReadFalse(1L)).thenReturn(0L);
        assertThat(service.getUnreadCount(1L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("markAllAsRead() should call repository to mark all as read")
    void markAllAsRead_callsRepository() {
        doNothing().when(notificationRepository).markAllAsRead(1L);
        service.markAllAsRead(1L);
        verify(notificationRepository).markAllAsRead(1L);
    }
}
