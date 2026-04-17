package talan.pfe.rulengine.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.dtos.response.NotificationResponse;
import talan.pfe.rulengine.entites.Notification;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.repositories.NotificationRepository;
import talan.pfe.rulengine.repositories.TenantRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "notifications", groupId = "notification-consumer-group")
    public void consume(NotificationEvent event) {
        try {
            // 1. Save to DB
            Tenant tenant = null;
            if (event.getTenantId() != null) {
                tenant = tenantRepository.findById(event.getTenantId()).orElse(null);
            }

            Notification notification = Notification.builder()
                    .title(event.getTitle())
                    .message(event.getMessage())
                    .type(event.getType())
                    .entityId(event.getEntityId())
                    .entityType(event.getEntityType())
                    .tenant(tenant)
                    .read(false)
                    .build();

            Notification saved = notificationRepository.save(notification);

            // 2. Push via WebSocket
            NotificationResponse response = toDto(saved);
            String destination = event.getTenantId() != null
                    ? "/topic/notifications/" + event.getTenantId()
                    : "/topic/notifications/global";

            messagingTemplate.convertAndSend(destination, response);
            log.debug("Notification pushed to {}: {}", destination, event.getTitle());

        } catch (Exception e) {
            log.error("Failed to process notification: {}", event.getTitle(), e);
        }
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