package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.SimulationRequest;
import talan.pfe.rulengine.dtos.response.SimulationResult;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.RuleSimulationServiceImpl;

@Tag(name = "Simulation", description = "Simulation d'un RuleSet sur des données de test sans persistance")
@RestController
@RequestMapping("/api/ruleset")
@RequiredArgsConstructor
public class SimulationController {

    private final RuleSimulationServiceImpl simulationService;
    private final JwtService jwtService;

    @Operation(summary = "Simuler l'exécution d'un RuleSet sans persistance")
    @PostMapping("/{ruleSetId}/simulate")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<SimulationResult> simulate(
            @PathVariable Long ruleSetId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SimulationRequest request) {

        Long tenantId = extractTenantId(authHeader);
        SimulationResult result = simulationService.simulate(ruleSetId, tenantId, request);
        return ResponseEntity.ok(result);
    }

    private Long extractTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId) : null;
    }
}