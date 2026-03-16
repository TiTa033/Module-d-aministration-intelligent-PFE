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
import java.util.UUID;

@RestController
@RequestMapping("/api/rulesets/{ruleSetId}/rules/{ruleId}/conditions")
@RequiredArgsConstructor
@Tag(name = "RuleCondition Management",
        description = "CRUD operations for RuleConditions inside a Rule")
public class RuleConditionController {

    private final RuleConditionService conditionService;
    private final JwtService jwtService;

    private UUID getTenantId(String authHeader) {
        return UUID.fromString(
                jwtService.extractTenantId(authHeader.substring(7)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Create a new RuleCondition")
    public ResponseEntity<RuleConditionResponse> create(
            @PathVariable UUID ruleSetId,
            @PathVariable UUID ruleId,
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
            @PathVariable UUID ruleSetId,
            @PathVariable UUID ruleId,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                conditionService.getAll(ruleSetId, ruleId, getTenantId(authHeader)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get RuleCondition by ID")
    public ResponseEntity<RuleConditionResponse> getById(
            @PathVariable UUID ruleSetId,
            @PathVariable UUID ruleId,
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        return ResponseEntity.ok(
                conditionService.getById(ruleSetId, ruleId, id, getTenantId(authHeader)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Update a RuleCondition")
    public ResponseEntity<RuleConditionResponse> update(
            @PathVariable UUID ruleSetId,
            @PathVariable UUID ruleId,
            @PathVariable UUID id,
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
            @PathVariable UUID ruleSetId,
            @PathVariable UUID ruleId,
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        conditionService.delete(ruleSetId, ruleId, id, getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }
}

