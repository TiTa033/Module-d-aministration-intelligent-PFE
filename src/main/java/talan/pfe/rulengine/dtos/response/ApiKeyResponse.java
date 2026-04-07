package talan.pfe.rulengine.dtos.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyResponse {

    private Long id;
    private String name;
    private boolean active;
    private boolean expired;
    private String keyPrefix;   // first 8 chars e.g. "raas_a1b" — shown in list
    private Long tenantId;
    private String tenantName;
    private Long ruleSetId;
    private String ruleSetName;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}