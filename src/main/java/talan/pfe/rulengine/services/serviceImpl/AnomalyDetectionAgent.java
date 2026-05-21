package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.*;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.AuditLogRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;

import talan.pfe.rulengine.services.llm.LlmClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionAgent {

    private final AuditLogRepository auditLogRepository;
    private final AiInsightRepository aiInsightRepository;
    private final RuleSetRepository ruleSetRepository;
    private final TenantRepository tenantRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AiNotificationService aiNotificationService;

    static final int RAPID_CHANGE_THRESHOLD = 3;
    static final int RAPID_CHANGE_WINDOW_MINUTES = 60;
    static final int SUSPICIOUS_USER_THRESHOLD = 15;

    private record AnomalyFinding(String type, String severity, String description, String entity, String entityId) {}

    private static final Set<AuditAction> MODIFICATION_ACTIONS = Set.of(
            AuditAction.RULE_UPDATED, AuditAction.RULE_ENABLED, AuditAction.RULE_DISABLED,
            AuditAction.CONDITION_UPDATED, AuditAction.CONDITION_CREATED, AuditAction.CONDITION_DELETED,
            AuditAction.ACTION_UPDATED, AuditAction.ACTION_CREATED, AuditAction.ACTION_DELETED,
            AuditAction.RULESET_UPDATED
    );

    private static final Set<Operator> NUMERIC_OPERATORS = Set.of(
            Operator.GREATER_THAN, Operator.GREATER_OR_EQUAL,
            Operator.LESS_THAN, Operator.LESS_OR_EQUAL
    );

    @Async
    @Transactional
    public void analyzeAllTenants() {
        log.info("AnomalyDetectionAgent: starting anomaly scan for all active tenants");
        tenantRepository.findAll().stream()
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
                .forEach(t -> {
                    try {
                        runAnalysis(t);
                    } catch (Exception e) {
                        log.error("Anomaly analysis failed for tenant id={} name={}", t.getId(), t.getName(), e);
                    }
                });
    }

    @Async
    @Transactional
    public void analyzeForTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            log.warn("AnomalyDetectionAgent: tenant id={} not found", tenantId);
            return;
        }
        try {
            runAnalysis(tenant);
        } catch (Exception e) {
            log.error("Anomaly analysis failed for tenant id={}", tenantId, e);
        }
    }

    private void runAnalysis(Tenant tenant) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(RAPID_CHANGE_WINDOW_MINUTES);
        List<AuditLog> recentLogs = auditLogRepository.findByTenantIdAndTimestampAfter(tenant.getId(), since);

        List<RuleSet> ruleSets = ruleSetRepository.findAllByTenantId(tenant.getId());
        for (RuleSet rs : ruleSets) {
            rs.getRules().forEach(r -> {
                r.getConditions().size();
                r.getActions().size();
            });
        }

        List<AnomalyFinding> findings = new ArrayList<>();
        findings.addAll(detectRapidParameterChanges(recentLogs));
        findings.addAll(detectSuspiciousUserBehavior(recentLogs));
        findings.addAll(detectRuleConflicts(ruleSets));
        findings.addAll(detectSuspiciousValues(ruleSets));

        if (findings.isEmpty()) {
            log.debug("No anomalies detected for tenant id={}", tenant.getId());
            return;
        }

        String description = generateAnalysis(tenant, findings);
        String severity = dominantSeverity(findings);
        String title = "Anomalies détectées – " + findings.size() + " problème(s) [" + severity + "]";
        String contextData = buildContextData(tenant, findings, recentLogs.size());
        String suggestion = buildSuggestionJson(findings);
        float confidence = computeConfidence(findings);

        List<AiInsight> pendingList = aiInsightRepository.findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(
                tenant.getId(), InsightType.ANOMALY, InsightStatus.PENDING);

        if (!pendingList.isEmpty()) {
            // keep the most recent, delete all duplicates
            if (pendingList.size() > 1) {
                aiInsightRepository.deleteAll(pendingList.subList(1, pendingList.size()));
                log.info("Anomaly dedup: deleted {} duplicate PENDING insight(s) for tenant id={}",
                        pendingList.size() - 1, tenant.getId());
            }
            AiInsight insight = pendingList.get(0);
            insight.setTitle(title);
            insight.setDescription(description);
            insight.setContextData(contextData);
            insight.setSuggestion(suggestion);
            insight.setConfidence(confidence);
            insight.setGeneratedAt(LocalDateTime.now());
            aiInsightRepository.save(insight);
            log.info("Anomaly insight updated for tenant id={} ({} findings, severity={})",
                    tenant.getId(), findings.size(), severity);
        } else {
            AiInsight insight = AiInsight.builder()
                    .type(InsightType.ANOMALY)
                    .agentType(AgentType.ANOMALY_DETECTION_AGENT)
                    .title(title)
                    .description(description)
                    .contextData(contextData)
                    .suggestion(suggestion)
                    .confidence(confidence)
                    .tenant(tenant)
                    .build();
            aiInsightRepository.save(insight);
            log.info("Anomaly insight created for tenant id={} ({} findings, severity={})",
                    tenant.getId(), findings.size(), severity);
        }

        int criticalCount = (int) findings.stream().filter(f -> "CRITICAL".equals(f.severity())).count();
        int highCount     = (int) findings.stream().filter(f -> "HIGH".equals(f.severity())).count();
        aiNotificationService.notifyAnomalyDetected(tenant.getId(), title, criticalCount, highCount, findings.size());
    }

    // ── Detection methods ────────────────────────────────────────────────────

    private List<AnomalyFinding> detectRapidParameterChanges(List<AuditLog> logs) {
        Map<String, Long> countByEntity = logs.stream()
                .filter(l -> MODIFICATION_ACTIONS.contains(l.getAction()))
                .collect(Collectors.groupingBy(
                        l -> l.getEntityType() + "#" + l.getEntityId(),
                        Collectors.counting()
                ));

        return countByEntity.entrySet().stream()
                .filter(e -> e.getValue() >= RAPID_CHANGE_THRESHOLD)
                .map(e -> {
                    String[] parts = e.getKey().split("#", 2);
                    return new AnomalyFinding(
                            "RAPID_PARAMETER_CHANGE", "HIGH",
                            "L'entité " + parts[0] + " (id=" + parts[1] + ") a été modifiée "
                                    + e.getValue() + " fois en " + RAPID_CHANGE_WINDOW_MINUTES
                                    + " minutes. Cela peut indiquer des modifications instables ou non maîtrisées.",
                            parts[0], parts[1]
                    );
                })
                .toList();
    }

    private List<AnomalyFinding> detectSuspiciousUserBehavior(List<AuditLog> logs) {
        Map<String, Long> countByUser = logs.stream()
                .filter(l -> l.getUser() != null)
                .collect(Collectors.groupingBy(
                        l -> l.getUser().getId() + ":" + l.getUser().getEmail(),
                        Collectors.counting()
                ));

        return countByUser.entrySet().stream()
                .filter(e -> e.getValue() >= SUSPICIOUS_USER_THRESHOLD)
                .map(e -> {
                    String[] parts = e.getKey().split(":", 2);
                    return new AnomalyFinding(
                            "SUSPICIOUS_USER_ACTIVITY", "MEDIUM",
                            "L'utilisateur " + parts[1] + " a effectué " + e.getValue()
                                    + " modifications en " + RAPID_CHANGE_WINDOW_MINUTES
                                    + " minutes. Comportement potentiellement anormal.",
                            "User", parts[0]
                    );
                })
                .toList();
    }

    private List<AnomalyFinding> detectRuleConflicts(List<RuleSet> ruleSets) {
        List<AnomalyFinding> findings = new ArrayList<>();
        for (RuleSet rs : ruleSets) {
            List<Rule> enabled = rs.getRules().stream().filter(Rule::isEnabled).toList();

            // Duplicate priorities cause non-deterministic evaluation in FIRST_MATCH
            if (rs.getEvaluationStrategy() == EvaluationStrategy.FIRST_MATCH) {
                Map<Integer, Long> byPriority = enabled.stream()
                        .collect(Collectors.groupingBy(Rule::getPriority, Collectors.counting()));
                byPriority.entrySet().stream()
                        .filter(e -> e.getValue() > 1)
                        .forEach(e -> findings.add(new AnomalyFinding(
                                "DUPLICATE_RULE_PRIORITY", "CRITICAL",
                                "RuleSet '" + rs.getName() + "' (FIRST_MATCH) – " + e.getValue()
                                        + " règles activées partagent la priorité " + e.getKey()
                                        + ". Le résultat d'évaluation est non déterministe.",
                                "RuleSet", String.valueOf(rs.getId())
                        )));
            }

            // SCORE_BASED rules must have a score defined
            if (rs.getEvaluationStrategy() == EvaluationStrategy.SCORE_BASED) {
                enabled.stream()
                        .filter(r -> r.getScore() == null)
                        .forEach(r -> findings.add(new AnomalyFinding(
                                "MISSING_SCORE_IN_SCORE_BASED", "HIGH",
                                "Règle '" + r.getName() + "' dans le RuleSet '" + rs.getName()
                                        + "' (SCORE_BASED) n'a pas de score défini. Le calcul de score sera faussé.",
                                "Rule", String.valueOf(r.getId())
                        )));
            }

            for (Rule rule : enabled) {
                detectContradictoryConditions(rule, rs.getName(), findings);
            }
        }
        return findings;
    }

    private void detectContradictoryConditions(Rule rule, String ruleSetName, List<AnomalyFinding> findings) {
        Map<String, List<RuleCondition>> byField = rule.getConditions().stream()
                .collect(Collectors.groupingBy(RuleCondition::getField));

        for (Map.Entry<String, List<RuleCondition>> entry : byField.entrySet()) {
            List<RuleCondition> conds = entry.getValue();
            if (conds.size() < 2) continue;
            String field = entry.getKey();

            // Multiple EQUALS on same field with different values → never satisfiable
            List<String> eqValues = conds.stream()
                    .filter(c -> c.getOperator() == Operator.EQUALS)
                    .map(RuleCondition::getValue)
                    .distinct()
                    .toList();
            if (eqValues.size() > 1) {
                findings.add(new AnomalyFinding(
                        "CONTRADICTORY_EQ_CONDITIONS", "CRITICAL",
                        "Règle '" + rule.getName() + "' (RuleSet: " + ruleSetName + ") – Le champ '"
                                + field + "' a plusieurs conditions EQUALS incompatibles: " + eqValues
                                + ". Cette règle ne sera jamais satisfaite.",
                        "Rule", String.valueOf(rule.getId())
                ));
            }

            // Impossible numeric range: lower bound >= upper bound
            if (conds.stream().allMatch(c -> c.getValueType() == DataType.NUMBER)) {
                try {
                    OptionalDouble lowerBound = conds.stream()
                            .filter(c -> c.getOperator() == Operator.GREATER_THAN
                                    || c.getOperator() == Operator.GREATER_OR_EQUAL)
                            .mapToDouble(c -> Double.parseDouble(c.getValue()))
                            .max();
                    OptionalDouble upperBound = conds.stream()
                            .filter(c -> c.getOperator() == Operator.LESS_THAN
                                    || c.getOperator() == Operator.LESS_OR_EQUAL)
                            .mapToDouble(c -> Double.parseDouble(c.getValue()))
                            .min();
                    if (lowerBound.isPresent() && upperBound.isPresent()
                            && lowerBound.getAsDouble() >= upperBound.getAsDouble()) {
                        findings.add(new AnomalyFinding(
                                "IMPOSSIBLE_CONDITION_RANGE", "CRITICAL",
                                "Règle '" + rule.getName() + "' (RuleSet: " + ruleSetName + ") – Champ '"
                                        + field + "': plage impossible (valeur > " + lowerBound.getAsDouble()
                                        + " ET < " + upperBound.getAsDouble()
                                        + "). Aucune valeur ne peut satisfaire cette condition.",
                                "Rule", String.valueOf(rule.getId())
                        ));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private List<AnomalyFinding> detectSuspiciousValues(List<RuleSet> ruleSets) {
        List<AnomalyFinding> findings = new ArrayList<>();
        for (RuleSet rs : ruleSets) {
            for (Rule rule : rs.getRules()) {
                for (RuleCondition cond : rule.getConditions()) {
                    if (cond.getValue() == null || cond.getValue().isBlank()) {
                        findings.add(new AnomalyFinding(
                                "EMPTY_CONDITION_VALUE", "HIGH",
                                "Règle '" + rule.getName() + "' (RuleSet: " + rs.getName()
                                        + ") – La condition sur le champ '" + cond.getField()
                                        + "' a une valeur vide. L'évaluation produira des résultats incorrects.",
                                "RuleCondition", String.valueOf(cond.getId())
                        ));
                    } else if (cond.getValueType() == DataType.NUMBER
                            && NUMERIC_OPERATORS.contains(cond.getOperator())
                            && !isNumeric(cond.getValue())) {
                        findings.add(new AnomalyFinding(
                                "TYPE_MISMATCH_CONDITION", "HIGH",
                                "Règle '" + rule.getName() + "' (RuleSet: " + rs.getName()
                                        + ") – Condition '" + cond.getField() + " " + cond.getOperator()
                                        + " " + cond.getValue()
                                        + "': opérateur numérique avec une valeur non numérique.",
                                "RuleCondition", String.valueOf(cond.getId())
                        ));
                    }
                }
            }
        }
        return findings;
    }

    // ── LLM analysis + fallback ──────────────────────────────────────────────

    private String generateAnalysis(Tenant tenant, List<AnomalyFinding> findings) {
        String systemPrompt = """
                Tu es un expert en sécurité des systèmes de règles métier et en détection d'anomalies.
                Tu analyses des problèmes détectés automatiquement dans un moteur de règles métier.
                Tu fournis une analyse claire, précise et des recommandations concrètes en français.
                Ton ton est professionnel, direct et orienté vers l'action.
                """;

        try {
            return llmClient.generate(systemPrompt, buildAnalysisPrompt(tenant, findings));
        } catch (Exception e) {
            log.warn("LLM unavailable for anomaly analysis (tenant={}), using fallback: {}",
                    tenant.getId(), e.getMessage());
            return buildFallback(tenant, findings);
        }
    }

    private String buildAnalysisPrompt(Tenant tenant, List<AnomalyFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTE : Rapport d'anomalies automatique pour le tenant '")
                .append(tenant.getName()).append("'\n\n");
        sb.append("PROBLÈMES DÉTECTÉS (").append(findings.size()).append(") :\n\n");
        findings.forEach(f -> sb.append("- [").append(f.severity()).append("] ")
                .append(f.type()).append(" : ").append(f.description()).append("\n"));

        sb.append("""

                STRUCTURE ATTENDUE (400 à 600 mots) :

                [RÉSUMÉ EXÉCUTIF]
                Synthèse claire des anomalies détectées et de leur impact potentiel sur le système de règles.

                [ANALYSE DES RISQUES]
                Pour chaque anomalie CRITICAL ou HIGH, décris le risque métier concret et les conséquences possibles.

                [RECOMMANDATIONS PRIORITAIRES]
                Liste de 3 à 5 actions correctives concrètes, ordonnées par urgence décroissante.

                [MESURES PRÉVENTIVES]
                Comment éviter ces anomalies à l'avenir : processus, contrôles et bonnes pratiques recommandés.

                CRITÈRES DE QUALITÉ :
                - Langage accessible à un gestionnaire métier non-technique
                - Recommandations actionnables et réalistes
                - Identification claire des priorités
                """);
        return sb.toString();
    }

    private String buildFallback(Tenant tenant, List<AnomalyFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Rapport d'anomalies – Tenant \"").append(tenant.getName()).append("\"\n\n");
        sb.append("### Résumé\n");
        sb.append(findings.size()).append(" anomalie(s) détectée(s) :\n");
        sb.append("- CRITICAL : ").append(countBySeverity(findings, "CRITICAL")).append("\n");
        sb.append("- HIGH : ").append(countBySeverity(findings, "HIGH")).append("\n");
        sb.append("- MEDIUM : ").append(countBySeverity(findings, "MEDIUM")).append("\n\n");
        sb.append("### Détail des problèmes\n");
        findings.forEach(f -> sb.append("**[").append(f.severity()).append("] ")
                .append(f.type()).append("**\n")
                .append(f.description()).append("\n\n"));
        sb.append("### Actions recommandées\n");
        sb.append("1. Vérifier immédiatement les anomalies de sévérité CRITICAL.\n");
        sb.append("2. Corriger les conditions contradictoires et les valeurs suspectes.\n");
        sb.append("3. Examiner l'historique des modifications récentes dans les logs d'audit.\n");
        sb.append("4. Mettre en place un processus de revue pour les changements critiques.\n\n");
        sb.append("*Note : Analyse générée en mode dégradé (LLM indisponible).*\n");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private long countBySeverity(List<AnomalyFinding> findings, String severity) {
        return findings.stream().filter(f -> severity.equals(f.severity())).count();
    }

    private String dominantSeverity(List<AnomalyFinding> findings) {
        if (findings.stream().anyMatch(f -> "CRITICAL".equals(f.severity()))) return "CRITICAL";
        if (findings.stream().anyMatch(f -> "HIGH".equals(f.severity()))) return "HIGH";
        if (findings.stream().anyMatch(f -> "MEDIUM".equals(f.severity()))) return "MEDIUM";
        return "LOW";
    }

    private float computeConfidence(List<AnomalyFinding> findings) {
        if (findings.stream().anyMatch(f -> "CRITICAL".equals(f.severity()))) return 0.95f;
        if (findings.stream().anyMatch(f -> "HIGH".equals(f.severity()))) return 0.85f;
        return 0.70f;
    }

    private String buildContextData(Tenant tenant, List<AnomalyFinding> findings, int auditLogsCount) {
        return toJson(Map.of(
                "tenantId", tenant.getId(),
                "tenantName", tenant.getName(),
                "totalFindings", findings.size(),
                "criticalCount", countBySeverity(findings, "CRITICAL"),
                "highCount", countBySeverity(findings, "HIGH"),
                "mediumCount", countBySeverity(findings, "MEDIUM"),
                "auditLogsAnalyzed", auditLogsCount,
                "windowMinutes", RAPID_CHANGE_WINDOW_MINUTES
        ));
    }

    private String buildSuggestionJson(List<AnomalyFinding> findings) {
        List<Map<String, String>> list = findings.stream()
                .map(f -> Map.of(
                        "type", f.type(),
                        "severity", f.severity(),
                        "entity", f.entity(),
                        "entityId", f.entityId(),
                        "description", f.description()
                ))
                .toList();
        return toJson(list);
    }

    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
