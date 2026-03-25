package talan.pfe.rulengine.dtos.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleSetResponse {

    private Long id;
    private String name;
    private String description;
    private String evaluationStrategy;
    private String status;
    private Integer currentVersion;
    private Long tenantId;
    private String tenantName;
    private int totalRules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}