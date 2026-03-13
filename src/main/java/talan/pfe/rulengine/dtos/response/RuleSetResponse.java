package talan.pfe.rulengine.dtos.response;

import lombok.*;
import talan.pfe.rulengine.entites.RuleSet;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleSetResponse {

    private UUID id;
    private String name;
    private String description;
    private String evaluationStrategy;
    private String status;
    private Integer currentVersion;
    private UUID tenantId;
    private String tenantName;
    private int totalRules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RuleSetResponse from(RuleSet ruleSet) {
        return RuleSetResponse.builder()
                .id(ruleSet.getId())
                .name(ruleSet.getName())
                .description(ruleSet.getDescription())
                .evaluationStrategy(ruleSet.getEvaluationStrategy().name())
                .status(ruleSet.getStatus().name())
                .currentVersion(ruleSet.getCurrentVersion())
                .tenantId(ruleSet.getTenant().getId())
                .tenantName(ruleSet.getTenant().getName())
                .totalRules(ruleSet.getRules().size())
                .createdAt(ruleSet.getCreatedAt())
                .updatedAt(ruleSet.getUpdatedAt())
                .build();
    }
}