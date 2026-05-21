package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.InsightStatusUpdateRequest;
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.dtos.response.AnomalyAlertResponse;
import talan.pfe.rulengine.enums.InsightType;
import talan.pfe.rulengine.repositories.AiInsightRepository;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.AiInsightService;
import talan.pfe.rulengine.services.serviceImpl.AnomalyDetectionAgent;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Détection d'Anomalies IA",
        description = "Agent IA de détection automatique d'anomalies, conflits de règles et comportements suspects")
public class AnomalyDetectionController {

    private final AnomalyDetectionAgent anomalyDetectionAgent;
    private final AiInsightRepository aiInsightRepository;
    private final AiInsightService aiInsightService;
    private final JwtService jwtService;

    private Long getTenantId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        if (tenantId == null || tenantId.equals("null") || tenantId.isBlank()) return null;
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @PostMapping("/api/anomalies/trigger")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Déclencher manuellement l'analyse d'anomalies pour le tenant courant")
    public ResponseEntity<Void> triggerAnalysis(
            @RequestHeader("Authorization") String authHeader) {
        Long tenantId = getTenantId(authHeader);
        if (tenantId == null) return ResponseEntity.badRequest().build();
        anomalyDetectionAgent.analyzeForTenant(tenantId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/anomalies")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Lister tous les rapports d'anomalies IA pour le tenant courant")
    public ResponseEntity<List<AnomalyAlertResponse>> listAnomalies(
            @RequestHeader("Authorization") String authHeader) {
        Long tenantId = getTenantId(authHeader);
        if (tenantId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(
                aiInsightRepository.findByTenantIdAndTypeOrderByGeneratedAtDesc(tenantId, InsightType.ANOMALY)
                        .stream()
                        .map(AnomalyAlertResponse::from)
                        .toList()
        );
    }

    @GetMapping("/api/anomalies/latest")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Obtenir le dernier rapport d'anomalies IA pour le tenant courant")
    public ResponseEntity<AnomalyAlertResponse> latestAnomaly(
            @RequestHeader("Authorization") String authHeader) {
        Long tenantId = getTenantId(authHeader);
        if (tenantId == null) return ResponseEntity.notFound().build();
        return aiInsightRepository
                .findFirstByTenantIdAndTypeOrderByGeneratedAtDesc(tenantId, InsightType.ANOMALY)
                .map(AnomalyAlertResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/api/anomalies/{insightId}/status")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Accepter ou rejeter une alerte d'anomalie")
    public ResponseEntity<AiInsightResponse> updateStatus(
            @PathVariable Long insightId,
            @Valid @RequestBody InsightStatusUpdateRequest request) {
        return ResponseEntity.ok(aiInsightService.updateStatus(insightId, request));
    }
}
