package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.services.llm.LlmClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformGuideAgent {

    private final LlmClient llmClient;

    public String generateGuide() {
        String systemPrompt = """
                Tu es un expert en automatisation de regles metier et en plateformes Rules as a Service (RaaS).
                Tu rediges des guides pratiques pour des utilisateurs metier.
                Reponds uniquement en francais, de facon claire, structuree et orientee action.
                """;

        String userPrompt = """
                Genere un guide pratique complet pour utiliser une plateforme RaaS (Rules as a Service).

                La plateforme permet de :
                - Creer et gerer des RuleSets (ensembles de regles metier)
                - Definir des regles avec conditions et actions
                - Choisir une strategie d'evaluation : FIRST_MATCH, ALL_MATCH ou SCORE_BASED
                - Versionner et activer les RuleSets
                - Tester via un playground avec des cles API
                - Suivre les evaluations et les audits
                - Generer de la documentation IA automatiquement

                Structure le guide en 7 etapes numerotees :
                1) Preparer le contexte metier
                2) Creer un RuleSet
                3) Concevoir des regles robustes
                4) Tester avant activation
                5) Activer et versionner
                6) Exploitation en production
                7) Bonnes pratiques avancees

                Pour chaque etape, indique l'objectif, les actions concretes et des conseils pratiques.
                Redige entre 600 et 900 mots au total.
                """;

        try {
            return llmClient.generate(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("LLM unavailable for guide generation, using static fallback: {}", e.getMessage());
            return buildStaticFallback();
        }
    }

    private String buildStaticFallback() {
        return """
                Guide pratique — Utiliser le moteur de regles (RaaS)
                Conversion = d
                Flags = #

                1) Preparer le contexte metier
                # Conversion d: clarifier l'objectif metier et les donnees d'entree/sortie.
                - Identifiez le cas d'usage (scoring credit, KYC, eligibilite, anti-fraude).
                - Listez les champs d'entree attendus avec leurs types.
                - Definissez la decision attendue en sortie.

                2) Creer un RuleSet
                # Conversion d: transformer le besoin metier en configuration RuleSet exploitable.
                - Donnez un nom metier explicite et une description orientee usage.
                - Choisissez la strategie : FIRST_MATCH, ALL_MATCH ou SCORE_BASED.
                - Gardez le RuleSet en DRAFT tant que les tests ne sont pas termines.

                3) Concevoir des regles robustes
                # Conversion d: decomposer chaque decision en regles, conditions et actions.
                - Renseignez la priorite, la logique (AND/OR), les conditions et les actions.
                - Conditions : champ + operateur + valeur + type coherent.
                - Evitez les chevauchements de regles contradictoires.

                4) Tester avant activation
                # Conversion d: verifier que la logique metier produise la bonne sortie.
                - Utilisez le playground avec une cle API liee au RuleSet.
                - Testez des cas nominaux, des cas limites et des cas invalides.
                - Corrigez en DRAFT jusqu'a stabilite.

                5) Activer et versionner
                # Conversion d: passer de la conception a l'execution gouvernee en production.
                - Activez le RuleSet apres validation metier.
                - A chaque activation, une version est tracee automatiquement.
                - La documentation IA est generee et peut etre acceptee ou rejetee.

                6) Exploitation en production
                # Flags #: surveiller les signaux de risque et les anomalies operationnelles.
                - Surveillez l'historique des evaluations pour detecter des anomalies.
                - Consultez les logs d'audit pour la tracabilite.
                - Faites evoluer les regles par petites iterations versionnees.

                7) Bonnes pratiques avancees
                # Flags #: maintenir la qualite et la maintenabilite des regles.
                - Un RuleSet = un objectif metier clair.
                - Utilisez des noms de regles orientes intention.
                - Documentez chaque changement de version.
                - Maintenez un cycle : concevoir, tester, activer, observer, ameliorer.
                """;
    }
}
