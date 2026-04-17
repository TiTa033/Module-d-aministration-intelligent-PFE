package talan.pfe.rulengine.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.enums.NotifType;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private static final String TOPIC = "notifications";
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void publish(String title,
                        String message,
                        NotifType type,
                        Long tenantId,
                        Long entityId,
                        String entityType) {
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .title(title)
                    .message(message)
                    .type(type)
                    .tenantId(tenantId)
                    .entityId(entityId)
                    .entityType(entityType)
                    .createdAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(TOPIC, String.valueOf(tenantId), event);
            log.debug("Notification published: {} — {}", title, message);

        } catch (Exception e) {
            // Notification failure must NEVER break the main request
            log.error("Failed to publish notification: {} — {} — {}",
                    title, message, e.getMessage());
        }
    }
}