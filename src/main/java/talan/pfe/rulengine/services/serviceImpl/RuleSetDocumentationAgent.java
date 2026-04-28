package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleSetDocumentationAgent {

    private final RuleSetRepository ruleSetRepository;
    private final AiInsightRepository aiInsightRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void generateDocumentation(Long ruleSetId, Long tenantId) {
        try {
            RuleSet ruleSet = ruleSetRepository.findByIdAndTenantIdWithRules(ruleSetId, tenantId)
                    .orElse(null);
            if (ruleSet == null) {
                return;
            }
            for (Rule rule : ruleSet.getRules()) {
                rule.getConditions().size();
                rule.getActions().size();
            }

            String systemPrompt = """
                    Tu es un expert en règles métier financières.
                    Tu rédiges des documentations utiles pour des gestionnaires non-techniques.
                    Réponds uniquement en français, clair et concret.
                    """;

            String userPrompt = buildPrompt(ruleSet);
            String content;
            try {
                content = llmClient.generate(systemPrompt, userPrompt);
            } catch (Exception e) {
                log.warn("LLM unavailable for documentation generation, using fallback text: {}", e.getMessage());
                content = buildFallback(ruleSet);
            }

            AiInsight insight = AiInsight.builder()
                    .type(InsightType.DOCUMENTATION_GENERATED)
                    .agentType(AgentType.DOCUMENTATION_AGENT)
                    .title("Documentation IA - RuleSet " + ruleSet.getName() + " (v" + ruleSet.getCurrentVersion() + ")")
                    .description(content)
                    .contextData(toJson(Map.of(
                            "strategy", String.valueOf(ruleSet.getEvaluationStrategy()),
                            "ruleCount", ruleSet.getRules().size(),
                            "ruleSetId", ruleSet.getId(),
                            "version", ruleSet.getCurrentVersion()
                    )))
                    .confidence(0.87f)
                    .tenant(ruleSet.getTenant())
                    .ruleSet(ruleSet)
                    .build();
            aiInsightRepository.save(insight);
        } catch (Exception e) {
            log.error("Failed to generate RuleSet documentation for id={}", ruleSetId, e);
        }
    }

    private String buildPrompt(RuleSet ruleSet) {
        StringJoiner joiner = new StringJoiner("\n");
        int i = 1;
        for (Rule rule : ruleSet.getRules()) {
            joiner.add("Règle " + i + " : " + rule.getName());
            joiner.add("  - Priorité: " + rule.getPriority() + ", activée: " + rule.isEnabled());
            joiner.add("  - Conditions:");
            rule.getConditions().forEach(c ->
                    joiner.add("      * " + c.getField() + " " + c.getOperator() + " " + c.getValue() + " (" + c.getValueType() + ")"));
            joiner.add("  - Actions:");
            rule.getActions().forEach(a ->
                    joiner.add("      * " + a.getActionType() + " => " + a.getOutputKey() + "=" + a.getOutputValue()));
            i++;
        }
        return """
                Voici un RuleSet:
                Nom: %s
                Description: %s
                Stratégie d'évaluation: %s
                
                Détails des règles:
                %s
                
                Génère une documentation concise (120 à 220 mots) en français.
                Inclure ces sections:
                1) Objectif du RuleSet
                2) Comment il décide
                3) Lecture métier des principales règles
                4) Quand l'utiliser et limites
                """.formatted(
                ruleSet.getName(),
                ruleSet.getDescription() == null ? "(non renseignée)" : ruleSet.getDescription(),
                ruleSet.getEvaluationStrategy(),
                joiner
        );
    }

    private String buildFallback(RuleSet ruleSet) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ce RuleSet \"").append(ruleSet.getName()).append("\" applique la stratégie ")
                .append(ruleSet.getEvaluationStrategy()).append(". ");
        sb.append("Il contient ").append(ruleSet.getRules().size()).append(" règle(s) évaluées par ordre de priorité. ");
        for (Rule rule : ruleSet.getRules()) {
            sb.append("La règle \"").append(rule.getName()).append("\" vérifie ")
                    .append(rule.getConditions().size()).append(" condition(s) ")
                    .append("et exécute ").append(rule.getActions().size()).append(" action(s). ");
        }
        sb.append("Utilisez ce RuleSet pour automatiser des décisions homogènes et auditables.");
        return sb.toString();
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
