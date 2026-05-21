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
                    Tu es un expert en regles metier financieres.
                    Tu rediges des documentations utiles pour des gestionnaires non-techniques.
                    Reponds uniquement en francais, de facon claire et concrete.
                    """;

            String content;
            try {
                content = llmClient.generate(systemPrompt, buildPrompt(ruleSet));
            } catch (Exception e) {
                log.warn("LLM unavailable for documentation, using fallback: {}", e.getMessage());
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
            joiner.add("Regle " + i + " : " + rule.getName());
            joiner.add("  - Priorite: " + rule.getPriority() + ", activee: " + rule.isEnabled());
            joiner.add("  - Conditions:");
            rule.getConditions().forEach(c ->
                    joiner.add("      * " + c.getField() + " " + c.getOperator() + " " + c.getValue() + " (" + c.getValueType() + ")"));
            joiner.add("  - Actions:");
            rule.getActions().forEach(a ->
                    joiner.add("      * " + a.getActionType() + " => " + a.getOutputKey() + "=" + a.getOutputValue()));
            i++;
        }

        return """
                CONTEXTE : Documentation metier d'un RuleSet

                Genere une documentation pedagogique et detaillee du RuleSet suivant.

                RULESET :
                Nom: %s
                Description: %s
                Strategie d'evaluation: %s
                Nombre de regles: %d

                DETAIL DES REGLES :
                %s

                STRUCTURE ATTENDUE (500 a 800 mots minimum) :

                [SECTION 1 - OBJECTIF ET CAS D'USAGE]
                - Probleme metier resolu par ce RuleSet
                - Cas d'usage concret et realiste
                - Utilisateurs concernes (RH, risque, commercial, etc.)

                [SECTION 2 - FLUX DE FONCTIONNEMENT]
                - Explication de la strategie "%s" en detail
                - Ordre d'execution et importance de la priorite

                [SECTION 3 - EXPLICATION DE CHAQUE REGLE]
                Pour chaque regle, redige un paragraphe expliquant son objectif metier,
                ses conditions en langage naturel et les actions resultantes.

                [SECTION 4 - SCENARIOS D'APPLICATION]
                Donne 2 a 3 exemples concrets de donnees et leurs resultats.

                [SECTION 5 - LIMITATIONS ET RISQUES]
                - Cas non couverts par ce RuleSet
                - Risques si mal utilise

                [SECTION 6 - RECOMMANDATIONS]
                - 2 a 3 ameliorations concretes suggerees

                CRITERES DE QUALITE :
                - Redaction accessible a un responsable metier non-technique
                - Exemples concrets et realistes
                - Identification des cas limites
                - Vocabulaire metier, pas de jargon technique
                """.formatted(
                ruleSet.getName(),
                ruleSet.getDescription() == null ? "(non renseignee)" : ruleSet.getDescription(),
                ruleSet.getEvaluationStrategy(),
                ruleSet.getRules().size(),
                joiner,
                ruleSet.getEvaluationStrategy()
        );
    }

    private String buildFallback(RuleSet ruleSet) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Documentation - RuleSet \"").append(ruleSet.getName()).append("\" (v")
                .append(ruleSet.getCurrentVersion()).append(")\n\n");

        sb.append("### Vue d'ensemble\n");
        sb.append("- **Strategie d'evaluation** : ").append(ruleSet.getEvaluationStrategy()).append("\n");
        sb.append("- **Nombre de regles** : ").append(ruleSet.getRules().size()).append("\n");
        sb.append("- **Description metier** : ")
                .append(ruleSet.getDescription() != null ? ruleSet.getDescription() : "Non documentee")
                .append("\n\n");

        sb.append("### Fonctionnement\n");
        sb.append("Ce RuleSet applique la strategie ").append(ruleSet.getEvaluationStrategy())
                .append(" pour automatiser des decisions metier.\n");
        sb.append("Les regles sont evaluees par ordre de priorite. Voici le detail :\n\n");

        int index = 1;
        for (Rule rule : ruleSet.getRules()) {
            sb.append("**Regle ").append(index).append(" : ").append(rule.getName()).append("**\n");
            sb.append("- Priorite : ").append(rule.getPriority()).append("\n");
            sb.append("- Etat : ").append(rule.isEnabled() ? "Activee" : "Desactivee").append("\n");
            sb.append("- Conditions (").append(rule.getConditions().size()).append(") :\n");
            rule.getConditions().forEach(c ->
                    sb.append("  * ").append(c.getField()).append(" ").append(c.getOperator())
                            .append(" ").append(c.getValue()).append("\n"));
            sb.append("- Actions (").append(rule.getActions().size()).append(") :\n");
            rule.getActions().forEach(a ->
                    sb.append("  * ").append(a.getActionType()).append(" : ")
                            .append(a.getOutputKey()).append(" = ").append(a.getOutputValue()).append("\n"));
            sb.append("\n");
            index++;
        }

        sb.append("### Utilisation\n");
        sb.append("Utilisez ce RuleSet pour automatiser des decisions metier de facon homogene et tracable.\n\n");

        sb.append("### Note\n");
        sb.append("Cette documentation a ete generee en mode fallback (LLM indisponible).\n");
        sb.append("Pour une analyse complete avec cas d'usage, regenerez la documentation IA.\n");

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
