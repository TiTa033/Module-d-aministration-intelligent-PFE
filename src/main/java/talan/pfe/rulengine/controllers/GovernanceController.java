package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.response.GovernanceSummaryResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.GovernanceService;

@Tag(name = "Governance", description = "Tableau de bord de gouvernance du tenant")
@RestController
@RequestMapping("/api/governance")
@RequiredArgsConstructor
public class GovernanceController {

    private final GovernanceService governanceService;
    private final JwtService        jwtService;

    @Operation(summary = "Résumé de gouvernance : règles actives, évaluations du jour, alertes, notifications")
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<GovernanceSummaryResponse> getSummary(
            @RequestHeader("Authorization") String authHeader) {

        Long tenantId = extractTenantId(authHeader);
        return ResponseEntity.ok(governanceService.getSummary(tenantId));
    }

    private Long extractTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId) : null;
    }
}
