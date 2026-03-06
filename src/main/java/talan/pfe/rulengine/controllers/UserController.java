package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.mappers.UserMapper;
import talan.pfe.rulengine.services.UserService;

import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/tenants/{tenantId}/users")
@Tag(name = "Utilisateurs")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@PathVariable UUID tenantId,
                                              @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userMapper.toDto(userService.create(request, tenantId)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAll(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(userMapper.toDtoList(userService.getByTenant(tenantId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID tenantId,
                                               @PathVariable UUID id) {
        return ResponseEntity.ok(userMapper.toDto(userService.getById(id)));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer un utilisateur")
    public ResponseEntity<UserResponse> activate(@PathVariable UUID tenantId,
                                                 @PathVariable UUID id) {
        return ResponseEntity.ok(userMapper.toDto(userService.setActive(id, true)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un utilisateur")
    public ResponseEntity<UserResponse> deactivate(@PathVariable UUID tenantId,
                                                   @PathVariable UUID id) {
        return ResponseEntity.ok(userMapper.toDto(userService.setActive(id, false)));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Changer le rôle d'un utilisateur")
    public ResponseEntity<UserResponse> changeRole(@PathVariable UUID tenantId,
                                                   @PathVariable UUID id,
                                                   @RequestParam Role role) {
        return ResponseEntity.ok(userMapper.toDto(userService.changeRole(id, role)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID tenantId,
                                       @PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}