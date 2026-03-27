package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.services.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs")
public class UserController {

    private final UserService userService;
    // ← UserMapper removed — service handles mapping now

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(
            @PathVariable Long tenantId,
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.create(request, tenantId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VIEWER')")
    public ResponseEntity<List<UserResponse>> getAll(
            @PathVariable Long tenantId) {
        return ResponseEntity.ok(userService.getByTenant(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VIEWER')")
    public ResponseEntity<UserResponse> getById(
            @PathVariable Long tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id, tenantId));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer un utilisateur")
    public ResponseEntity<UserResponse> activate(
            @PathVariable Long tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(userService.setActive(id, tenantId, true));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un utilisateur")
    public ResponseEntity<UserResponse> deactivate(
            @PathVariable Long tenantId,
            @PathVariable Long id) {
        return ResponseEntity.ok(userService.setActive(id, tenantId, false));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Changer le rôle d'un utilisateur")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long tenantId,
            @PathVariable Long id,
            @RequestParam Role role) {
        return ResponseEntity.ok(userService.changeRole(id, tenantId, role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long tenantId,
            @PathVariable Long id) {
        userService.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}