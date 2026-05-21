package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import talan.pfe.rulengine.dtos.request.N8nInsightPayload;
import talan.pfe.rulengine.dtos.response.CollectWithSuggestionsResponse;
import talan.pfe.rulengine.dtos.response.SuggestedActionDto;
import talan.pfe.rulengine.dtos.response.SuggestedConditionDto;
import talan.pfe.rulengine.dtos.response.SuggestedRuleDto;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.llm.LlmClient;
import talan.pfe.rulengine.services.llm.TavilyWebSearchClient;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalDataCollectionAgent {

    private final TavilyWebSearchClient tavilyClient;
    private final LlmClient llmClient;
    private final AiInsightRepository aiInsightRepository;
    private final TenantRepository tenantRepository;
    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final ObjectMapper objectMapper;
    private final AiNotificationService aiNotificationService;

    @Async
    @Transactional
    public void collectAndAnalyze() {
        log.info("ExternalDataCollectionAgent: starting daily financial data collection");

        String period = LocalDate.now().getMonth().name() + " " + LocalDate.now().getYear();

        Map<String, String> collected = new LinkedHashMap<>();
        collected.put("Banque Centrale", fetch(
                "central bank interest rate monetary policy decision " + period, "Banque Centrale"));
        collected.put("Macro-économique", fetch(
                "inflation GDP growth unemployment macroeconomic indicators " + period, "Macro-économique"));
        collected.put("Sectoriel", fetch(
                "banking sector fintech credit risk financial regulation " + period, "Sectoriel"));

        boolean hasData = collected.values().stream().anyMatch(v -> v != null && !v.isBlank());
        if (!hasData) {
            log.warn("ExternalDataCollectionAgent: no data collected from Tavily, aborting");
            return;
        }

        String analysis = generateAnalysis(collected, period);
        String contextJson = toJson(Map.of(
                "period", period,
                "collectedAt", LocalDate.now().toString(),
                "sources", List.of("Tavily Web Search", "Banque Centrale", "Macro-éco", "Sectoriel")
        ));

        List<Tenant> activeTenants = tenantRepository.findAll().stream()
                .filter(t -> TenantStatus.ACTIVE.equals(t.getStatus()))
                .collect(Collectors.toList());

        String title = "Analyse Externe – " + period + " [Banque Centrale · Macro-éco · Sectoriel]";
        String truncatedAnalysis = truncate(analysis, 2000);
        int saved = 0;
        for (Tenant tenant : activeTenants) {
            if (aiInsightRepository.existsByTenantIdAndTypeAndTitle(
                    tenant.getId(), InsightType.EXTERNAL_RECOMMENDATION, title)) {
                log.debug("ExternalDataCollectionAgent: insight already exists for tenant id={} period={}, skipping",
                        tenant.getId(), period);
                continue;
            }
            AiInsight insight = AiInsight.builder()
                    .type(InsightType.EXTERNAL_RECOMMENDATION)
                    .agentType(AgentType.EXTERNAL_ANALYSIS_AGENT)
                    .title(title)
                    .description(truncatedAnalysis)
                    .contextData(contextJson)
                    .confidence(0.82f)
                    .tenant(tenant)
                    .build();
            aiInsightRepository.save(insight);
            aiNotificationService.notifyExternalAnalysisReady(tenant.getId(), period, title);
            saved++;
        }

        log.info("ExternalDataCollectionAgent: saved insight for {}/{} active tenant(s) ({} already existed)",
                saved, activeTenants.size(), activeTenants.size() - saved);
    }

    private String fetch(String query, String category) {
        try {
            TavilyWebSearchClient.SearchResponse response = tavilyClient.search(query, 5);
            if (response == null) return "";

            StringBuilder sb = new StringBuilder();
            if (response.getAnswer() != null && !response.getAnswer().isBlank()) {
                sb.append(response.getAnswer()).append("\n");
            }
            if (response.getResults() != null) {
                for (TavilyWebSearchClient.ResultItem item : response.getResults()) {
                    if (item.getContent() != null) {
                        int len = Math.min(300, item.getContent().length());
                        sb.append("- ").append(item.getTitle()).append(": ")
                                .append(item.getContent(), 0, len).append("\n");
                    }
                }
            }
            log.debug("Collected {} chars for '{}'", sb.length(), category);
            return sb.toString();
        } catch (Exception e) {
            log.error("Fetch failed for '{}': {}", category, e.getMessage());
            return "";
        }
    }

    private String generateAnalysis(Map<String, String> data, String period) {
        String systemPrompt = """
                Tu es un analyste financier expert. Tu analyses les données macro-économiques
                et sectorielles pour aider les équipes de gestion des risques à adapter leurs règles métier.
                Réponds uniquement en français, de façon claire, structurée et orientée décision.
                """;

        StringBuilder prompt = new StringBuilder();
        prompt.append("PÉRIODE D'ANALYSE : ").append(period).append("\n\n");

        data.forEach((cat, content) -> {
            if (content != null && !content.isBlank()) {
                prompt.append("=== ").append(cat).append(" ===\n");
                prompt.append(content, 0, Math.min(1500, content.length())).append("\n\n");
            }
        });

        prompt.append("""
                STRUCTURE DE L'ANALYSE ATTENDUE :

                [1. RÉSUMÉ EXÉCUTIF]
                Points clés pour les décideurs (3-4 bullet points)

                [2. IMPACT BANQUE CENTRALE]
                - Décisions de politique monétaire et impact sur les règles de scoring crédit

                [3. INDICATEURS MACRO-ÉCONOMIQUES]
                - Tendances inflation / croissance / emploi et implications pour les règles de risque

                [4. ANALYSE SECTORIELLE]
                - Évolutions réglementaires ou sectorielles et recommandations d'ajustement

                [5. RECOMMANDATIONS CONCRÈTES]
                - 3 actions spécifiques pour adapter les règles métier

                Sois factuel, précis et orienté vers l'action métier.
                """);

        try {
            return llmClient.generate(systemPrompt, prompt.toString());
        } catch (Exception e) {
            log.warn("LLM unavailable, using raw fallback: {}", e.getMessage());
            return buildFallback(data, period);
        }
    }

    private String buildFallback(Map<String, String> data, String period) {
        StringBuilder sb = new StringBuilder("## Rapport Externe – ").append(period).append("\n\n");
        sb.append("*Généré en mode fallback (LLM indisponible)*\n\n");
        data.forEach((cat, content) -> {
            sb.append("### ").append(cat).append("\n");
            sb.append(content != null && !content.isBlank()
                    ? content.substring(0, Math.min(500, content.length()))
                    : "Aucune donnée collectée.").append("\n\n");
        });
        return sb.toString();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
    }

    // ── Collect + rule suggestions ────────────────────────────────────────────

    public CollectWithSuggestionsResponse collectWithSuggestions(Long ruleSetId, Long tenantId) {
        String period = LocalDate.now().getMonth().name() + " " + LocalDate.now().getYear();

        // 1. Collect data (no transaction — external network calls)
        Map<String, String> collected = new LinkedHashMap<>();
        collected.put("Banque Centrale", fetch(
                "central bank interest rate monetary policy decision " + period, "Banque Centrale"));
        collected.put("Macro-économique", fetch(
                "inflation GDP growth unemployment macroeconomic indicators " + period, "Macro-économique"));
        collected.put("Sectoriel", fetch(
                "banking sector fintech credit risk financial regulation " + period, "Sectoriel"));

        // 2. Read RuleSet schema in a short isolated transaction
        Map<String, String> fieldSchema = new LinkedHashMap<>();
        Set<String> existingNames = new HashSet<>();
        int maxPriority = 0;

        if (ruleSetId != null && tenantId != null) {
            RuleSet rs = loadRuleSetWithRules(ruleSetId, tenantId);
            if (rs != null) {
                for (var rule : rs.getRules()) {
                    existingNames.add(rule.getName().toLowerCase(java.util.Locale.ROOT));
                    if (rule.getPriority() != null && rule.getPriority() > maxPriority) {
                        maxPriority = rule.getPriority();
                    }
                    for (var c : rule.getConditions()) {
                        if (c.getField() != null && c.getValueType() != null) {
                            fieldSchema.putIfAbsent(c.getField(), c.getValueType().name());
                        }
                    }
                }
            }
        }

        // 3. Call LLM (no transaction — long external call)
        List<SuggestedRuleDto> suggestions = generateRuleSuggestions(collected, period, fieldSchema, maxPriority);

        for (SuggestedRuleDto r : suggestions) {
            boolean dup = existingNames.contains(r.getName().toLowerCase(java.util.Locale.ROOT));
            r.setAlreadyExists(dup);
            if (dup) r.setDuplicateReason("Une règle avec ce nom existe déjà dans le RuleSet.");
        }

        long newCount      = suggestions.stream().filter(r -> !Boolean.TRUE.equals(r.getAlreadyExists())).count();
        long existingCount = suggestions.stream().filter(r ->  Boolean.TRUE.equals(r.getAlreadyExists())).count();

        return CollectWithSuggestionsResponse.builder()
                .collectedData(collected)
                .suggestedRules(suggestions)
                .fetchedAt(LocalDate.now().toString())
                .collectionMethod("TAVILY_WEB_SEARCH + LLM")
                .tenantsAffected(1)
                .newRulesCount((int) newCount)
                .existingRulesCount((int) existingCount)
                .build();
    }

    @Transactional(readOnly = true)
    protected RuleSet loadRuleSetWithRules(Long ruleSetId, Long tenantId) {
        return ruleSetRepository.findByIdAndTenantIdWithRules(ruleSetId, tenantId).orElse(null);
    }

    private List<SuggestedRuleDto> generateRuleSuggestions(
            Map<String, String> data, String period,
            Map<String, String> fieldSchema, int maxPriority) {

        String system = """
                Tu es un expert en règles métier financières.
                Tu génères UNIQUEMENT du JSON valide, sans aucun texte avant ou après, sans bloc markdown.
                """;

        StringBuilder prompt = new StringBuilder();
        prompt.append("DONNÉES FINANCIÈRES – ").append(period).append("\n\n");
        data.forEach((cat, content) -> {
            if (content != null && !content.isBlank()) {
                prompt.append("### ").append(cat).append("\n")
                      .append(content, 0, Math.min(700, content.length())).append("\n\n");
            }
        });

        if (!fieldSchema.isEmpty()) {
            prompt.append("CHAMPS EXISTANTS DANS LE RULESET (utilise ces champs en priorité) :\n");
            fieldSchema.forEach((f, t) -> prompt.append("  - ").append(f).append(" (").append(t).append(")\n"));
        } else {
            prompt.append("CHAMPS FINANCIERS TYPIQUES DISPONIBLES :\n");
            prompt.append("  - credit_score (NUMBER), loan_amount (NUMBER), income (NUMBER)\n");
            prompt.append("  - debt_ratio (NUMBER), employment_status (STRING), country (STRING)\n");
            prompt.append("  - loan_duration_months (NUMBER), collateral_value (NUMBER)\n");
        }
        prompt.append("\nOPÉRATEURS : EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, GREATER_OR_EQUAL, LESS_OR_EQUAL, CONTAINS, IS_NULL, IS_NOT_NULL\n");
        prompt.append("TYPES : STRING, NUMBER, BOOLEAN, DATE\n");
        prompt.append("ACTIONS : SET_VALUE, SET_FLAG, SET_SCORE, REJECT, APPROVE, REQUIRE_REVIEW\n");
        prompt.append("LOGIQUE : AND, OR\n\n");
        prompt.append("Génère 3 règles métier JSON adaptées aux données financières ci-dessus.\n");
        prompt.append("Priorités à partir de ").append(maxPriority + 1).append(".\n");
        prompt.append("Format STRICT (tableau JSON uniquement) :\n");
        prompt.append("""
                [
                  {
                    "name": "Nom de la règle (max 100 caractères)",
                    "description": "Justification métier",
                    "priority": %d,
                    "logicOperator": "AND",
                    "score": null,
                    "conditions": [
                      {"field": "credit_score", "operator": "LESS_THAN", "value": "600", "valueType": "NUMBER"}
                    ],
                    "actions": [
                      {"actionType": "REJECT", "outputKey": "decision", "outputValue": "REFUSÉ"}
                    ],
                    "reasoning": "Justification basée sur les données collectées",
                    "confidence": 0.85
                  }
                ]
                """.formatted(maxPriority + 1));

        try {
            String raw = llmClient.generate(system, prompt.toString());
            log.debug("LLM rule suggestions raw response ({} chars): {}",
                    raw == null ? 0 : raw.length(),
                    raw == null ? "null" : raw.substring(0, Math.min(200, raw.length())));
            List<SuggestedRuleDto> result = parseRuleSuggestions(raw);
            log.info("Parsed {} rule suggestion(s) from LLM response", result.size());
            return result;
        } catch (Exception e) {
            log.warn("LLM unavailable for rule suggestions: {}", e.getMessage());
            return List.of();
        }
    }

    private List<SuggestedRuleDto> parseRuleSuggestions(String raw) {
        String json = extractJsonArray(raw);
        if (json == null) {
            log.warn("No JSON array found in LLM response for rule suggestions");
            return List.of();
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            return items.stream().map(this::mapToSuggestedRule).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.warn("Failed to parse rule suggestions: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        if (text == null || text.isBlank()) return null;

        // Strip markdown fences first
        String working = text;
        if (text.contains("```")) {
            int fence = text.indexOf("```");
            int afterFence = text.indexOf('\n', fence);
            if (afterFence >= 0) working = text.substring(afterFence + 1);
        }

        // Prefer [{ pattern (JSON array of objects)
        int s = working.indexOf("[{");
        if (s < 0) s = working.indexOf("[ {");
        // Fallback: any [
        if (s < 0) s = working.indexOf('[');

        int e = working.lastIndexOf(']');
        if (s < 0 || e <= s) return null;

        String candidate = working.substring(s, e + 1).trim();
        // Validate it starts and ends correctly
        if (candidate.startsWith("[") && candidate.endsWith("]")) return candidate;
        return null;
    }

    private SuggestedRuleDto mapToSuggestedRule(Map<String, Object> m) {
        try {
            String name = str(m, "name");
            if (name == null || name.isBlank()) return null;

            return SuggestedRuleDto.builder()
                    .name(name)
                    .description(str(m, "description"))
                    .priority(intVal(m, "priority", 10))
                    .logicOperator(str(m, "logicOperator") != null ? str(m, "logicOperator") : "AND")
                    .score(intOrNull(m, "score"))
                    .conditions(parseConditions(m))
                    .actions(parseActions(m))
                    .reasoning(str(m, "reasoning"))
                    .confidence(doubleVal(m, "confidence", 0.75))
                    .alreadyExists(false)
                    .build();
        } catch (Exception e) {
            log.warn("Skipping malformed rule entry: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<SuggestedConditionDto> parseConditions(Map<String, Object> m) {
        Object raw = m.get("conditions");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(c -> c instanceof Map)
                .map(c -> (Map<?, ?>) c)
                .filter(c -> c.get("field") != null && c.get("operator") != null)
                .map(c -> SuggestedConditionDto.builder()
                        .field(String.valueOf(c.get("field")))
                        .operator(String.valueOf(c.get("operator")))
                        .value(c.get("value") != null ? String.valueOf(c.get("value")) : "")
                        .valueType(c.get("valueType") != null ? String.valueOf(c.get("valueType")) : "STRING")
                        .build())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<SuggestedActionDto> parseActions(Map<String, Object> m) {
        Object raw = m.get("actions");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(a -> a instanceof Map)
                .map(a -> (Map<?, ?>) a)
                .filter(a -> a.get("actionType") != null && a.get("outputKey") != null)
                .map(a -> SuggestedActionDto.builder()
                        .actionType(String.valueOf(a.get("actionType")))
                        .outputKey(String.valueOf(a.get("outputKey")))
                        .outputValue(a.get("outputValue") != null ? String.valueOf(a.get("outputValue")) : "")
                        .build())
                .toList();
    }

    private String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private int intVal(Map<?, ?> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        try { return ((Number) v).intValue(); } catch (Exception e) { return def; }
    }

    private Integer intOrNull(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return ((Number) v).intValue(); } catch (Exception e) { return null; }
    }

    private double doubleVal(Map<?, ?> m, String key, double def) {
        Object v = m.get(key);
        if (v == null) return def;
        try { return ((Number) v).doubleValue(); } catch (Exception e) { return def; }
    }

    // ── n8n ingestion ─────────────────────────────────────────────────────────

    /** Called by n8n after it has done the full collect + LLM analysis. */
    @Transactional
    public void saveFromN8nPayload(N8nInsightPayload payload) {
        log.info("ExternalDataCollectionAgent: saving n8n insight '{}'", payload.getTitle());

        String contextJson = toJson(Map.of(
                "period",       payload.getPeriod() != null ? payload.getPeriod() : LocalDate.now().toString(),
                "collectedAt",  LocalDate.now().toString(),
                "sources",      payload.getSources() != null ? payload.getSources() : List.of("n8n workflow"),
                "origin",       "N8N_WORKFLOW"
        ));

        float confidence = payload.getConfidence() != null ? payload.getConfidence() : 0.82f;

        List<Tenant> activeTenants = tenantRepository.findAll().stream()
                .filter(t -> TenantStatus.ACTIVE.equals(t.getStatus()))
                .collect(Collectors.toList());

        String truncatedDesc = truncate(payload.getDescription(), 2000);
        int saved = 0;
        for (Tenant tenant : activeTenants) {
            if (aiInsightRepository.existsByTenantIdAndTypeAndTitle(
                    tenant.getId(), InsightType.EXTERNAL_RECOMMENDATION, payload.getTitle())) {
                log.debug("ExternalDataCollectionAgent (n8n): insight already exists for tenant id={}, skipping",
                        tenant.getId());
                continue;
            }
            AiInsight insight = AiInsight.builder()
                    .type(InsightType.EXTERNAL_RECOMMENDATION)
                    .agentType(AgentType.EXTERNAL_ANALYSIS_AGENT)
                    .title(payload.getTitle())
                    .description(truncatedDesc)
                    .contextData(contextJson)
                    .confidence(confidence)
                    .tenant(tenant)
                    .build();
            aiInsightRepository.save(insight);
            aiNotificationService.notifyExternalAnalysisReady(tenant.getId(),
                    payload.getPeriod() != null ? payload.getPeriod() : LocalDate.now().toString(),
                    payload.getTitle());
            saved++;
        }

        log.info("ExternalDataCollectionAgent (n8n): saved insight for {}/{} active tenant(s) ({} already existed)",
                saved, activeTenants.size(), activeTenants.size() - saved);
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}