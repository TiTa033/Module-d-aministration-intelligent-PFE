package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.CreateAlertConfigRequest;
import talan.pfe.rulengine.dtos.response.AlertConfigResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.AlertConfigService;

import java.util.List;

@Tag(name = "Alert Configuration", description = "Gestion des alertes de surveillance pour le tenant")
@RestController
@RequestMapping("/api/governance/alerts")
@RequiredArgsConstructor
public class AlertConfigController {

    private final AlertConfigService alertConfigService;
    private final JwtService         jwtService;

    @Operation(summary = "Créer une alerte de surveillance")
    @PostMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<AlertConfigResponse> create(
            @Valid @RequestBody CreateAlertConfigRequest request,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alertConfigService.create(request, extractTenantId(authHeader)));
    }

    @Operation(summary = "Lister les alertes du tenant")
    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<List<AlertConfigResponse>> getAll(
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(alertConfigService.getAll(extractTenantId(authHeader)));
    }

    @Operation(summary = "Activer / désactiver une alerte")
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<AlertConfigResponse> toggle(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                alertConfigService.toggleEnabled(id, extractTenantId(authHeader)));
    }

    @Operation(summary = "Supprimer une alerte")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        alertConfigService.delete(id, extractTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }

    private Long extractTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId) : null;
    }
}
