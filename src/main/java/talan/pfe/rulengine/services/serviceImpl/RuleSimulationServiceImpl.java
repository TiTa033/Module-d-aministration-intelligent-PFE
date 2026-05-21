package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.SimulationRequest;
import talan.pfe.rulengine.dtos.response.SimulationResult;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.repositories.*;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RuleSimulationServiceImpl {

    private final EvaluationRequestRepository evaluationRequestRepository;
    private final RuleRepository ruleRepository;
    private final LlmClient llmClient;

    public SimulationResult simulate(Long ruleSetId, Long tenantId,
                                     SimulationRequest request) {

        int sampleSize = request.getSampleSize() > 0 ? request.getSampleSize() : 100;

        List<EvaluationRequest> pastEvals = evaluationRequestRepository
                .findByTenantIdOrderByRequestedAtDesc(tenantId,
                        PageRequest.of(0, sampleSize))
                .getContent();

        if (pastEvals.isEmpty()) {
            return SimulationResult.builder()
                    .totalEvaluated(0)
                    .aiAnalysis("Aucune évaluation historique disponible pour simuler l'impact.")
                    .aiAvailable(false)
                    .build();
        }

        Rule originalRule = ruleRepository.findById(request.getRuleId())
                .orElseThrow(() -> new RuntimeException("Rule not found: " + request.getRuleId()));

        Rule simulatedRule = buildSimulatedRule(originalRule, request);

        List<SimulationResult.EvaluationDiff> diffs = new ArrayList<>();
        int changedCount = 0, improvedCount = 0, worsenedCount = 0;
        double totalScoreBefore = 0, totalScoreAfter = 0;

        for (EvaluationRequest eval : pastEvals) {
            try {
                Map<String, Object> payloadMap = parsePayload(eval.getInputPayload());

                double scoreBefore = evaluateRuleScore(originalRule, payloadMap);
                double scoreAfter  = evaluateRuleScore(simulatedRule, payloadMap);

                totalScoreBefore += scoreBefore;
                totalScoreAfter  += scoreAfter;

                boolean changed = Math.abs(scoreBefore - scoreAfter) > 0.001;
                if (changed) {
                    changedCount++;
                    if (scoreAfter > scoreBefore) improvedCount++;
                    else worsenedCount++;
                }

                if (diffs.size() < 10) {
                    diffs.add(SimulationResult.EvaluationDiff.builder()
                            .evaluationId(eval.getId())
                            .inputPayloadSummary(summarizePayload(payloadMap))
                            .scoreBefore(scoreBefore)
                            .scoreAfter(scoreAfter)
                            .resultChanged(changed)
                            .build());
                }
            } catch (Exception e) {
                log.warn("Skipping evaluation {} during simulation: {}",
                        eval.getId(), e.getMessage());
            }
        }

        int total = pastEvals.size();
        double avgBefore = total > 0 ? totalScoreBefore / total : 0;
        double avgAfter  = total > 0 ? totalScoreAfter  / total : 0;
        double changePct = total > 0 ? (changedCount * 100.0) / total : 0;

        String aiAnalysis = "Analyse IA indisponible.";
        boolean aiAvailable = false;

        try {
            aiAnalysis = generateAiAnalysis(
                    originalRule, simulatedRule, total,
                    changedCount, changePct, improvedCount,
                    worsenedCount, avgBefore, avgAfter);
            aiAvailable = true;
        } catch (Exception e) {
            log.error("Azure AI unavailable during simulation: {}", e.getMessage());
        }

        return SimulationResult.builder()
                .totalEvaluated(total)
                .changedCount(changedCount)
                .changePercentage(Math.round(changePct * 10.0) / 10.0)
                .improvedCount(improvedCount)
                .worsenedCount(worsenedCount)
                .avgScoreBefore(Math.round(avgBefore * 10.0) / 10.0)
                .avgScoreAfter(Math.round(avgAfter * 10.0) / 10.0)
                .avgScoreDelta(Math.round((avgAfter - avgBefore) * 10.0) / 10.0)
                .sampleDiffs(diffs)
                .aiAnalysis(aiAnalysis)
                .aiAvailable(aiAvailable)
                .build();
    }

    // ── Build simulated rule in memory ────────────────────────────────────
    private Rule buildSimulatedRule(Rule original, SimulationRequest request) {
        List<RuleCondition> simulatedConditions = request.getProposedConditions()
                .stream()
                .map(pc -> {
                    RuleCondition rc = new RuleCondition();
                    rc.setField(pc.getField());
                    // ✅ Convert String → Operator enum safely
                    try {
                        rc.setOperator(Operator.valueOf(pc.getOperator().toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        rc.setOperator(Operator.EQUALS);
                    }
                    rc.setValue(pc.getValue());
                    // ✅ Convert String → DataType enum safely
                    try {
                        rc.setValueType(DataType.valueOf(pc.getValueType().toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        rc.setValueType(DataType.STRING);
                    }
                    return rc;
                })
                .collect(Collectors.toList());

        Rule simulated = new Rule();
        simulated.setId(original.getId());
        simulated.setName(request.getNewRuleName() != null
                ? request.getNewRuleName() : original.getName());

        // ✅ Convert String → LogicOperator enum safely
        if (request.getNewLogicOperator() != null) {
            try {
                simulated.setLogicOperator(
                        LogicOperator.valueOf(request.getNewLogicOperator().toUpperCase()));
            } catch (IllegalArgumentException e) {
                simulated.setLogicOperator(original.getLogicOperator());
            }
        } else {
            simulated.setLogicOperator(original.getLogicOperator());
        }

        // ✅ score is Integer not Double
        if (request.getNewScore() != null) {
            simulated.setScore(request.getNewScore().intValue());
        } else {
            simulated.setScore(original.getScore());
        }

        simulated.setConditions(simulatedConditions);
        simulated.setActions(original.getActions());
        return simulated;
    }

    // ── Evaluate rule score against a payload ─────────────────────────────
    private double evaluateRuleScore(Rule rule, Map<String, Object> payload) {
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) return 0;

        boolean allMatch = true;
        boolean anyMatch = false;

        for (RuleCondition condition : rule.getConditions()) {
            boolean met = evaluateCondition(condition, payload);
            if (met) anyMatch = true;
            else allMatch = false;
        }

        // ✅ Compare enum not String
        boolean ruleMatches = (rule.getLogicOperator() == LogicOperator.OR)
                ? anyMatch : allMatch;

        return ruleMatches ? (rule.getScore() != null ? rule.getScore() : 0) : 0;
    }

    // ── Evaluate a single condition ───────────────────────────────────────
    private boolean evaluateCondition(RuleCondition cond, Map<String, Object> payload) {
        Object rawVal = payload.get(cond.getField());
        if (rawVal == null) return false;

        String condVal = cond.getValue();

        try {
            // ✅ cond.getOperator() is an Operator enum — use == or .name()
            Operator op = cond.getOperator();
            if (op == null) return false;

            switch (op) {
                case GREATER_THAN:
                    return toDouble(rawVal) > Double.parseDouble(condVal);
                case LESS_THAN:
                    return toDouble(rawVal) < Double.parseDouble(condVal);
                case GREATER_OR_EQUAL:
                    return toDouble(rawVal) >= Double.parseDouble(condVal);
                case LESS_OR_EQUAL:
                    return toDouble(rawVal) <= Double.parseDouble(condVal);
                case EQUALS:
                    return rawVal.toString().equalsIgnoreCase(condVal);
                case NOT_EQUALS:
                    return !rawVal.toString().equalsIgnoreCase(condVal);
                case CONTAINS:
                    return rawVal.toString().toLowerCase()
                            .contains(condVal.toLowerCase());
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.parseDouble(val.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String summarizePayload(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .limit(3)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    // ── LLM Analysis ──────────────────────────────────────────────────────
    private String generateAiAnalysis(
            Rule original, Rule simulated, int total, int changed,
            double changePct, int improved, int worsened,
            double avgBefore, double avgAfter) {

        String systemPrompt = """
                Tu es un expert en analyse de règles métier financières.
                Tu aides les administrateurs à décider si une modification de règle est bénéfique ou non.
                Réponds uniquement en français, de façon professionnelle et concise.
                Ne commence pas ta réponse par "Voici" ou "Bien sûr".
                """;

        String userPrompt = String.format("""
                Un administrateur souhaite modifier la règle suivante :

                RÈGLE ORIGINALE : "%s"
                - Score attribué : %d points
                - Opérateur logique : %s
                - Nombre de conditions : %d

                RÈGLE SIMULÉE (modification proposée) : "%s"
                - Nouveau score : %d points
                - Nouvel opérateur : %s
                - Nouvelles conditions : %d

                RÉSULTATS DE LA SIMULATION sur %d évaluations historiques réelles :
                - Évaluations dont le résultat change : %d (%.1f%%)
                - Évaluations améliorées (score monte) : %d
                - Évaluations détériorées (score baisse) : %d
                - Score moyen AVANT : %.1f points
                - Score moyen APRÈS : %.1f points
                - Delta moyen : %+.1f points

                Analyse l'impact de cette modification en 3-4 phrases claires et concises.
                Dis si tu recommandes d'appliquer la modification ou non, et pourquoi.
                """,
                original.getName(),
                original.getScore() != null ? original.getScore() : 0,
                original.getLogicOperator() != null ? original.getLogicOperator().name() : "AND",
                original.getConditions() != null ? original.getConditions().size() : 0,
                simulated.getName(),
                simulated.getScore() != null ? simulated.getScore() : 0,
                simulated.getLogicOperator() != null ? simulated.getLogicOperator().name() : "AND",
                simulated.getConditions() != null ? simulated.getConditions().size() : 0,
                total, changed, changePct, improved, worsened,
                avgBefore, avgAfter, (avgAfter - avgBefore)
        );

        return llmClient.generate(systemPrompt, userPrompt);
    }
}