package talan.pfe.rulengine.kafka;

import lombok.*;
import talan.pfe.rulengine.enums.NotifType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {

    private String title;
    private String message;
    private NotifType type;       // INFO, WARNING, SUCCESS, ERROR
    private Long tenantId;        // null = global
    private Long entityId;
    private String entityType;
    private LocalDateTime createdAt;
}