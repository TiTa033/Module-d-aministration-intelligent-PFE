package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.TenantResponse;
import talan.pfe.rulengine.services.TenantService;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Lookup", description = "Read tenant information for authenticated users")
public class TenantLookupController {

    private final TenantService tenantService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'VIEWER')")
    @Operation(summary = "Get tenant by ID for lookup")
    public ResponseEntity<TenantResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getById(id));
    }
}
