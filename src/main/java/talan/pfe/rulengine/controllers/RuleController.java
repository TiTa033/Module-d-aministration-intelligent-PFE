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
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleService;

import java.util.List;

@RestController
@RequestMapping("/api/rulesets/{ruleSetId}/rules")
@RequiredArgsConstructor
@Tag(name = "Rule Management",
        description = "CRUD operations for Rules inside a RuleSet")
public class RuleController {

    private final RuleService ruleService;
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
    @Operation(summary = "Create a new Rule inside a RuleSet")
    public ResponseEntity<RuleResponse> create(
            @PathVariable Long ruleSetId,
            @Valid @RequestBody CreateRuleRequest request,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ruleService.create(
                        ruleSetId, getTenantId(authHeader), request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get all Rules in a RuleSet — paginated")
    public ResponseEntity<PageResponse<RuleResponse>> getAll(
            @PathVariable Long ruleSetId,
            @RequestHeader("Authorization") String authHeader,
            @ModelAttribute RuleFilterRequest filter) {
        return ResponseEntity.ok(
                ruleService.getAll(ruleSetId, getTenantId(authHeader), filter));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get all Rules as a flat list ordered by priority")
    public ResponseEntity<List<RuleResponse>> getAllList(
            @PathVariable Long ruleSetId,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleService.getAllList(ruleSetId, getTenantId(authHeader)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get Rule by ID")
    public ResponseEntity<RuleResponse> getById(
            @PathVariable Long ruleSetId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleService.getById(ruleSetId, id, getTenantId(authHeader)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Update a Rule")
    public ResponseEntity<RuleResponse> update(
            @PathVariable Long ruleSetId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRuleRequest request,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(ruleService.update(
                ruleSetId, id, getTenantId(authHeader), request));
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Enable a Rule")
    public ResponseEntity<RuleResponse> enable(
            @PathVariable Long ruleSetId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleService.enable(ruleSetId, id, getTenantId(authHeader)));
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Disable a Rule")
    public ResponseEntity<RuleResponse> disable(
            @PathVariable Long ruleSetId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                ruleService.disable(ruleSetId, id, getTenantId(authHeader)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Delete a Rule")
    public ResponseEntity<Void> delete(
            @PathVariable Long ruleSetId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        ruleService.delete(ruleSetId, id, getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }
}