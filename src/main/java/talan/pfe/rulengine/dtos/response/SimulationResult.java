package talan.pfe.rulengine.dtos.response;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SimulationResult {

    // Stats quantitatives
    private int totalEvaluated;
    private int changedCount;          // évaluations dont le résultat change
    private double changePercentage;
    private int improvedCount;         // résultat "meilleur" (score monte)
    private int worsenedCount;         // résultat "moins bon" (score baisse)

    // Score moyen avant/après
    private double avgScoreBefore;
    private double avgScoreAfter;
    private double avgScoreDelta;

    // Détail par évaluation (max 10 pour l'UI)
    private List<EvaluationDiff> sampleDiffs;

    // Rapport IA généré par DeepSeek-R1
    private String aiAnalysis;
    private boolean aiAvailable;  // false si Ollama est down

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EvaluationDiff {
        private Long evaluationId;
        private String inputPayloadSummary;  // résumé du payload
        private Double scoreBefore;
        private Double scoreAfter;
        private boolean resultChanged;
    }
}