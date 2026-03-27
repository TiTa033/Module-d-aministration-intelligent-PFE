package talan.pfe.rulengine.dtos.response;

import lombok.*;

// Special response only returned on CREATE and REGENERATE
// Contains the raw key — shown ONCE then never again
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyCreatedResponse {

    private Long id;
    private String name;
    private String rawKey;      // full key shown once: "raas_a1b2c3d4e5f6..."
    private String keyPrefix;   // first 8 chars for display in list
    private Long tenantId;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime expiresAt;
}