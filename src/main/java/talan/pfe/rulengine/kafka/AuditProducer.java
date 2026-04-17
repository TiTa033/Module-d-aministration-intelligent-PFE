package talan.pfe.rulengine.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.enums.AuditAction;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditProducer {

    private static final String TOPIC = "audit-events";
    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;

    public void publish(AuditAction action,
                        String entityType,
                        Long entityId,
                        String oldValue,
                        String newValue,
                        Long tenantId,
                        Long userId,
                        String ipAddress) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(toJson(oldValue))
                    .newValue(toJson(newValue))
                    .tenantId(tenantId)
                    .userId(userId)
                    .ipAddress(ipAddress)
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(TOPIC, String.valueOf(entityId), event);
            log.debug("Audit event published: {} on {} id={}", action, entityType, entityId);

        } catch (Exception e) {
            // Audit failure must NEVER break the main request
            log.error("Failed to publish audit event: {} on {} id={} — {}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    private String toJson(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed;
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}