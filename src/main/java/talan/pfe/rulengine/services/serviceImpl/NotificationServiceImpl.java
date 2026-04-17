package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.response.NotificationResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.entites.Notification;
import talan.pfe.rulengine.repositories.NotificationRepository;
import talan.pfe.rulengine.services.NotificationService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public PageResponse<NotificationResponse> getAll(Long tenantId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository
                        .findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                        .map(this::toDto));
    }

    @Override
    public long getUnreadCount(Long tenantId) {
        return notificationRepository.countByTenantIdAndReadFalse(tenantId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long tenantId) {
        notificationRepository.markAllAsRead(tenantId);
    }

    private NotificationResponse toDto(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .read(n.isRead())
                .entityId(n.getEntityId())
                .entityType(n.getEntityType())
                .tenantId(n.getTenant() != null ? n.getTenant().getId() : null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}