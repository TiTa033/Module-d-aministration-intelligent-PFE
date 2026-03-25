package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleActionService;

import java.util.List;

@RestController
@RequestMapping("/api/rulesets/{ruleSetId}/rules/{ruleId}/actions")
@RequiredArgsConstructor
@Tag(name = "RuleAction Management",
        description = "CRUD operations for RuleActions inside a Rule")
public class RuleActionController {

    private final RuleActionService actionService;
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
    @Operation(summary = "Create a new RuleAction")
    public ResponseEntity<RuleActionResponse> create(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @Valid @RequestBody RuleActionRequest request,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actionService.create(
                        ruleSetId, ruleId, getTenantId(authHeader), request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get all RuleActions for a Rule")
    public ResponseEntity<List<RuleActionResponse>> getAll(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                actionService.getAll(ruleSetId, ruleId, getTenantId(authHeader)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get RuleAction by ID")
    public ResponseEntity<RuleActionResponse> getById(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                actionService.getById(ruleSetId, ruleId, id, getTenantId(authHeader)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Update a RuleAction")
    public ResponseEntity<RuleActionResponse> update(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @PathVariable Long id,
            @Valid @RequestBody RuleActionRequest request,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                actionService.update(ruleSetId, ruleId, id,
                        getTenantId(authHeader), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a RuleAction")
    public ResponseEntity<Void> delete(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        actionService.delete(ruleSetId, ruleId, id, getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }
}