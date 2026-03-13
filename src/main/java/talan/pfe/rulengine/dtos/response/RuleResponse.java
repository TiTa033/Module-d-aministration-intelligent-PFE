package talan.pfe.rulengine.dtos.response;

import lombok.*;
import talan.pfe.rulengine.entites.Rule;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleResponse {

    private UUID id;
    private String name;
    private String description;
    private Integer priority;
    private boolean enabled;
    private String logicOperator;
    private Integer score;
    private UUID ruleSetId;
    private String ruleSetName;
    private int totalConditions;
    private int totalActions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RuleResponse from(Rule rule) {
        return RuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .priority(rule.getPriority())
                .enabled(rule.isEnabled())
                .logicOperator(rule.getLogicOperator().name())
                .score(rule.getScore())
                .ruleSetId(rule.getRuleSet().getId())
                .ruleSetName(rule.getRuleSet().getName())
                .totalConditions(rule.getConditions().size())
                .totalActions(rule.getActions().size())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}