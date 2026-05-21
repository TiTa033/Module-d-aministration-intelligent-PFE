package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.CreateRulesFromSuggestionsRequest;
import talan.pfe.rulengine.dtos.request.N8nInsightPayload;
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.dtos.response.CollectWithSuggestionsResponse;
import talan.pfe.rulengine.dtos.response.CreateRulesFromSuggestionsResponse;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.ExternalDataCollectionAgent;
import talan.pfe.rulengine.services.serviceImpl.N8nWebhookService;
import talan.pfe.rulengine.services.serviceImpl.RuleSuggestionService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/external-analysis")
@Tag(name = "Analyse Externe IA", description = "Collecte quotidienne de données financières externes via Tavily (Banque Centrale, Macro-éco, Sectoriel)")
public class ExternalDataController {

    private final ExternalDataCollectionAgent externalDataCollectionAgent;
    private final RuleSuggestionService ruleSuggestionService;
    private final AiInsightRepository aiInsightRepository;
    private final JwtService jwtService;
    private final N8nWebhookService n8nWebhookService;

    @org.springframework.beans.factory.annotation.Value("${n8n.ingest.secret:}")
    private String n8nIngestSecret;

    @org.springframework.beans.factory.annotation.Value("${n8n.collect.webhook-url:}")
    private String collectWebhookUrl;

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Déclencher manuellement la collecte — délègue à n8n si configuré, sinon exécute l'agent Java")
    public ResponseEntity<Void> triggerNow() {
        if (collectWebhookUrl != null && !collectWebhookUrl.isBlank()) {
            n8nWebhookService.triggerCollectWorkflow();
        } else {
            externalDataCollectionAgent.collectAndAnalyze();
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Lister les insights d'analyse externe pour le tenant courant")
    public ResponseEntity<List<AiInsightResponse>> listInsights(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestHeader("Authorization") String authHeader) {

        Long tenantId = extractTenantId(authHeader);
        InsightStatus insightStatus;
        try {
            insightStatus = InsightStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            insightStatus = InsightStatus.PENDING;
        }

        List<AiInsightResponse> result = aiInsightRepository
                .findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(
                        tenantId, InsightType.EXTERNAL_RECOMMENDATION, insightStatus)
                .stream()
                .map(i -> AiInsightResponse.builder()
                        .id(i.getId())
                        .title(i.getTitle())
                        .description(i.getDescription())
                        .status(i.getStatus())
                        .generatedAt(i.getGeneratedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/collect-with-suggestions")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Collecte Tavily + génère des suggestions de règles via LLM (synchrone)")
    public ResponseEntity<CollectWithSuggestionsResponse> collectWithSuggestions(
            @RequestParam(required = false) Long ruleSetId,
            @RequestHeader("Authorization") String authHeader) {

        Long tenantId = extractTenantId(authHeader);
        CollectWithSuggestionsResponse response =
                externalDataCollectionAgent.collectWithSuggestions(ruleSetId, tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-suggested-rules")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Crée les règles suggérées sélectionnées par l'admin dans un RuleSet")
    public ResponseEntity<CreateRulesFromSuggestionsResponse> createSuggestedRules(
            @RequestBody CreateRulesFromSuggestionsRequest request,
            @RequestHeader("Authorization") String authHeader) {

        Long tenantId = extractTenantId(authHeader);
        CreateRulesFromSuggestionsResponse response =
                ruleSuggestionService.createFromSuggestions(
                        request.getRuleSetId(), tenantId, request.getRules());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ingest-insight")
    @Operation(summary = "Endpoint appelé par n8n une fois l'analyse LLM terminée")
    public ResponseEntity<Void> ingestFromN8n(
            @RequestBody N8nInsightPayload payload,
            @RequestHeader(value = "X-N8n-Ingest-Secret", required = false) String secret) {

        if (n8nIngestSecret != null && !n8nIngestSecret.isBlank()
                && !n8nIngestSecret.equals(secret)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        externalDataCollectionAgent.saveFromN8nPayload(payload);
        return ResponseEntity.accepted().build();
    }

    private Long extractTenantId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        if (tenantId == null || tenantId.equals("null") || tenantId.isBlank()) return null;
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}