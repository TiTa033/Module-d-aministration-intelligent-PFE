package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.response.RuleSetMetricsResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.MetricsService;

@Tag(name = "Metrics", description = "Métriques d'exécution et de performance par RuleSet")
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;
    private final JwtService     jwtService;

    /**
     * GET /api/metrics/rulesets/{ruleSetId}?period=7
     * period : 7 | 30 | 90 jours (défaut 30)
     */
    @Operation(summary = "Métriques d'un RuleSet sur une période (7, 30 ou 90 jours)")
    @GetMapping("/rulesets/{ruleSetId}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<RuleSetMetricsResponse> getRuleSetMetrics(
            @PathVariable Long ruleSetId,
            @RequestParam(defaultValue = "30") int period,
            @RequestHeader("Authorization") String authHeader) {

        Long tenantId = extractTenantId(authHeader);
        return ResponseEntity.ok(
                metricsService.getRuleSetMetrics(ruleSetId, tenantId, period));
    }

    private Long extractTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId) : null;
    }
}
