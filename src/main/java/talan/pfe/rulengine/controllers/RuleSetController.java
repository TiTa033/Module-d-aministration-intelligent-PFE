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

import java.util.UUID;

@RestController
@RequestMapping("/api/rulesets")
@RequiredArgsConstructor
@Tag(name = "RuleSet Management",
        description = "CRUD operations for RuleSet management")
public class RuleSetController {

    private final RuleSetService ruleSetService;
    private final JwtService jwtService;

    // ─── Helper: extract tenantId from JWT ──────────────────
    private UUID getTenantId(String authHeader) {
        String token = authHeader.substring(7);
        return UUID.fromString(jwtService.extractTenantId(token));
    }

    // ─── CREATE ─────────────────────────────────────────────
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

    // ─── GET ALL ────────────────────────────────────────────
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

    // ─── GET BY ID ──────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get RuleSet by ID")
    public ResponseEntity<RuleSetResponse> getById(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.getById(id, getTenantId(authHeader)));
    }

    // ─── UPDATE ─────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Update a RuleSet")
    public ResponseEntity<RuleSetResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRuleSetRequest request,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.update(id, getTenantId(authHeader), request));
    }

    // ─── ACTIVATE ───────────────────────────────────────────
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Activate a RuleSet")
    public ResponseEntity<RuleSetResponse> activate(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.activate(id, getTenantId(authHeader)));
    }

    // ─── ARCHIVE ────────────────────────────────────────────
    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Archive a RuleSet")
    public ResponseEntity<RuleSetResponse> archive(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.archive(id, getTenantId(authHeader)));
    }

    // ─── MOVE TO DRAFT ──────────────────────────────────────
    @PatchMapping("/{id}/draft")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Move RuleSet back to Draft")
    public ResponseEntity<RuleSetResponse> moveToDraft(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleSetService.moveToDraft(id, getTenantId(authHeader)));
    }

    // ─── DELETE ─────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Delete a RuleSet — only if not ACTIVE")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        ruleSetService.delete(id, getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }
}