package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleSetService;

@RestController
@RequestMapping("/api/rulesets")
@RequiredArgsConstructor
@Tag(name = "RuleSet Management",
        description = "CRUD operations for RuleSet management")
public class RuleSetController {

    private final RuleSetService ruleSetService;
    private final JwtService jwtService;

    private Long getTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(
                authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId)
                : null;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Create a new RuleSet")
    public ResponseEntity<RuleSetResponse> create(
            @Valid @RequestBody CreateRuleSetRequest request,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ruleSetService.create(request, getTenantId(authHeader)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get all RuleSets for the current tenant")
    public ResponseEntity<PageResponse<RuleSetResponse>> getAll(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ruleSetService.getAll(
                getTenantId(authHeader),
                search, status, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get RuleSet by ID")
    public ResponseEntity<RuleSetResponse> getById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.getById(id, getTenantId(authHeader)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Update a RuleSet")
    public ResponseEntity<RuleSetResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRuleSetRequest request,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.update(id, getTenantId(authHeader), request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Activate a RuleSet")
    public ResponseEntity<RuleSetResponse> activate(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.activate(id, getTenantId(authHeader)));
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Archive a RuleSet")
    public ResponseEntity<RuleSetResponse> archive(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.archive(id, getTenantId(authHeader)));
    }
    @PatchMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Unarchive a RuleSet (ADMIN only)")
    public ResponseEntity<RuleSetResponse> unarchive(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                ruleSetService.unarchive(id, getTenantId(authHeader)));
    }

    @PatchMapping("/{id}/draft")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Move RuleSet back to Draft")
    public ResponseEntity<RuleSetResponse> moveToDraft(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.moveToDraft(id, getTenantId(authHeader)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Delete a RuleSet — only if not ACTIVE")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        ruleSetService.delete(id, getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }
}