package talan.pfe.rulengine.dtos.request;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SimulationRequest {

    // La règle à simuler (modifiée mais pas encore sauvegardée)
    private Long ruleId;
    private String newRuleName;
    private String newLogicOperator;   // AND / OR
    // Change this field in SimulationRequest.java:
    private Integer newScore;  // was Double
    // Nouvelles conditions proposées
    private List<ProposedCondition> proposedConditions;

    // Combien d'évaluations passées à rejouer (défaut 100)
    private int sampleSize;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProposedCondition {
        private String field;
        private String operator;  // GREATER_THAN, LESS_THAN, EQUALS, CONTAINS...
        private String value;
        private String valueType; // NUMBER, STRING, BOOLEAN
    }
}