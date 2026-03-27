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
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.services.TenantService;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
@Tag(name = "Tenant Management",
        description = "CRUD operations for tenant management — GLOBAL_ADMIN only")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @Operation(summary = "Create a new tenant")
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tenantService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tenant by ID")
    public ResponseEntity<TenantResponse> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getById(id));
    }

    @GetMapping
    @Operation(summary = "Get all tenants — paginated with filters")
    public ResponseEntity<PageResponse<TenantResponse>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(
                tenantService.getAll(search, status, page, size, sortBy, sortDir));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update tenant name and description")
    public ResponseEntity<TenantResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a tenant")
    public ResponseEntity<TenantResponse> activate(
            @PathVariable Long id) {
        return ResponseEntity.ok(tenantService.activate(id));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a tenant")
    public ResponseEntity<TenantResponse> deactivate(
            @PathVariable Long id) {
        return ResponseEntity.ok(tenantService.deactivate(id));
    }


    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tenant — only if it has no users")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}