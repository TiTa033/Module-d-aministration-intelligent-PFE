package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.CreateApiKeyRequest;
import talan.pfe.rulengine.dtos.response.ApiKeyCreatedResponse;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.ApiKeyService;

import java.util.List;

@RestController
@RequestMapping("/api/apikeys")
@RequiredArgsConstructor
@Tag(name = "API Key Management",
        description = "Generate and manage API keys for external applications")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final JwtService jwtService;

    private Long getTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(
                authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId)
                : null;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Generate a new API Key")
    public ResponseEntity<ApiKeyCreatedResponse> generate(
            @Valid @RequestBody CreateApiKeyRequest request,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(apiKeyService.generate(
                        request, getTenantId(authHeader)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Get all API Keys for the current tenant")
    public ResponseEntity<List<ApiKeyResponse>> getAll(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                apiKeyService.getAll(getTenantId(authHeader)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Get API Key by ID")
    public ResponseEntity<ApiKeyResponse> getById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                apiKeyService.getById(id, getTenantId(authHeader)));
    }

    @PatchMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Revoke an API Key")
    public ResponseEntity<ApiKeyResponse> revoke(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                apiKeyService.revoke(id, getTenantId(authHeader)));
    }

    @PostMapping("/{id}/regenerate")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Regenerate an API Key — old key is revoked")
    public ResponseEntity<ApiKeyCreatedResponse> regenerate(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(
                apiKeyService.regenerate(id, getTenantId(authHeader)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Delete a revoked API Key")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        apiKeyService.delete(id, getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }
}