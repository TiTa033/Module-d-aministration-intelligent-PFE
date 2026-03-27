package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.RuleConditionService;

import java.util.List;

@RestController
@RequestMapping("/api/rulesets/{ruleSetId}/rules/{ruleId}/conditions")
@RequiredArgsConstructor
@Tag(name = "RuleCondition Management",
        description = "CRUD operations for RuleConditions inside a Rule")
public class RuleConditionController {

    private final RuleConditionService conditionService;
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
    @Operation(summary = "Create a new RuleCondition")
    public ResponseEntity<RuleConditionResponse> create(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @Valid @RequestBody RuleConditionRequest request,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conditionService.create(
                        ruleSetId, ruleId, getTenantId(authHeader), request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get all RuleConditions for a Rule")
    public ResponseEntity<List<RuleConditionResponse>> getAll(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                conditionService.getAll(
                        ruleSetId, ruleId, getTenantId(authHeader)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get RuleCondition by ID")
    public ResponseEntity<RuleConditionResponse> getById(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                conditionService.getById(
                        ruleSetId, ruleId, id, getTenantId(authHeader)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Update a RuleCondition")
    public ResponseEntity<RuleConditionResponse> update(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @PathVariable Long id,
            @Valid @RequestBody RuleConditionRequest request,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                conditionService.update(ruleSetId, ruleId, id,
                        getTenantId(authHeader), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a RuleCondition")
    public ResponseEntity<Void> delete(
            @PathVariable Long ruleSetId,
            @PathVariable Long ruleId,
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        conditionService.delete(ruleSetId, ruleId, id, getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }
}