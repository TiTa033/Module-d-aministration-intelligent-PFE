package talan.pfe.rulengine.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.entites.AuditLog;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.repositories.AuditLogRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditConsumer {

    private final AuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @KafkaListener(topics = "audit-events",
            groupId = "audit-consumer-group")
    public void consume(AuditEvent event) {
        try {
            Tenant tenant = null;
            if (event.getTenantId() != null) {
                tenant = tenantRepository.findById(event.getTenantId())
                        .orElse(null);
            }

            User user = null;
            if (event.getUserId() != null) {
                user = userRepository.findById(event.getUserId())
                        .orElse(null);
            }

            AuditLog log = AuditLog.builder()
                    .action(event.getAction())
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId())
                    .oldValue(event.getOldValue())
                    .newValue(event.getNewValue())
                    .ipAddress(event.getIpAddress())
                    .tenant(tenant)
                    .user(user)
                    .build();

            auditLogRepository.save(log);

        } catch (Exception e) {
            log.error("Failed to persist audit event: {}", event.getAction(), e);
        }
    }
}