package talan.pfe.rulengine.dtos.response;

import lombok.*;
import talan.pfe.rulengine.enums.NotifType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private String title;
    private String message;
    private NotifType type;
    private boolean read;
    private Long entityId;
    private String entityType;
    private Long tenantId;
    private LocalDateTime createdAt;
}