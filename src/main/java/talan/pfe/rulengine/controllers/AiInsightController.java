package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.InsightStatusUpdateRequest;
import talan.pfe.rulengine.dtos.response.AiInsightResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.AiInsightService;
import talan.pfe.rulengine.services.serviceImpl.DocumentationPdfService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Documentation IA", description = "Documentation générée par IA et validation (hors détail technique RuleSet)")
public class AiInsightController {

    private final AiInsightService aiInsightService;
    private final DocumentationPdfService documentationPdfService;
    private final JwtService jwtService;

    private Long getTenantId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        if (tenantId == null || tenantId.equals("null") || tenantId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @GetMapping("/api/rulesets/{ruleSetId}/insights/documentations")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "List all generated documentations for a RuleSet")
    public ResponseEntity<List<AiInsightResponse>> listDocumentation(
            @PathVariable Long ruleSetId,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                aiInsightService.getRuleSetDocumentation(ruleSetId, getTenantId(authHeader)));
    }

    @GetMapping("/api/rulesets/{ruleSetId}/insights/latest-documentation")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get latest generated documentation for a RuleSet")
    public ResponseEntity<AiInsightResponse> latestDocumentation(
            @PathVariable Long ruleSetId,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                aiInsightService.getLatestRuleSetDocumentation(ruleSetId, getTenantId(authHeader)));
    }

    @PatchMapping("/api/insights/{insightId}/status")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Accept or reject generated documentation")
    public ResponseEntity<AiInsightResponse> updateStatus(
            @PathVariable Long insightId,
            @Valid @RequestBody InsightStatusUpdateRequest request) {
        return ResponseEntity.ok(aiInsightService.updateStatus(insightId, request));
    }

    @PostMapping("/api/rulesets/{ruleSetId}/insights/generate")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Generate a new documentation insight asynchronously")
    public ResponseEntity<Void> generateNow(
            @PathVariable Long ruleSetId,
            @RequestHeader("Authorization") String authHeader) {
        aiInsightService.generateDocumentationNow(ruleSetId, getTenantId(authHeader));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/rulesets/{ruleSetId}/insights/latest-documentation/pdf")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Download latest RuleSet documentation as PDF")
    public ResponseEntity<byte[]> latestDocumentationPdf(
            @PathVariable Long ruleSetId,
            @RequestParam(value = "t", required = false) Long cacheBuster,
            @RequestHeader("Authorization") String authHeader) {
        byte[] pdf = documentationPdfService.buildRuleSetDocumentationPdf(ruleSetId, getTenantId(authHeader));
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ruleset-documentation-" + ruleSetId + "-" + stamp + ".pdf")
                .body(pdf);
    }

    @GetMapping("/api/documentation/engine-guide/pdf")
    @Operation(summary = "Download platform guide PDF")
    public ResponseEntity<byte[]> engineGuidePdf(
            @RequestParam(value = "t", required = false) Long cacheBuster) {
        byte[] pdf = documentationPdfService.buildEngineGuidePdf();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=guide-moteur-regles-" + stamp + ".pdf")
                .body(pdf);
    }
}
