package talan.pfe.rulengine.dtos.response;

import lombok.*;
import talan.pfe.rulengine.enums.AuditAction;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private Long id;
    private AuditAction action;
    private String entityType;
    private Long entityId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private LocalDateTime timestamp;
    private Long tenantId;
    private String tenantName;
    private Long userId;
    private String userEmail;
}