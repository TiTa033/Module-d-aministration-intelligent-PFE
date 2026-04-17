package talan.pfe.rulengine.kafka;

import lombok.*;
import talan.pfe.rulengine.enums.AuditAction;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    private AuditAction action;
    private String entityType;
    private Long entityId;
    private String oldValue;      // JSON string, nullable
    private String newValue;      // JSON string, nullable
    private String ipAddress;
    private Long tenantId;        // nullable for GLOBAL_ADMIN
    private Long userId;          // who performed the action
    private LocalDateTime timestamp;
}