/*package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import org.springframework.security.core.userdetails.User;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.services.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tenants/{tenantId}/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs")
public class UserController {

    private final UserService userService;


   @PostMapping
   @PreAuthorize("hasRole('ADMIN')")
   @Operation(summary = "Créer un utilisateur")
   public ResponseEntity<User> create(@PathVariable UUID tenantId, @Valid @RequestBody CreateUserRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, tenantId));
   }
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les utilisateurs du tenant")
    public ResponseEntity<List<User>> getAll(@PathVariable UUID tenantId) {

        return ResponseEntity.ok(userService.getByTenant(tenantId));
    }

   @GetMapping("/{id}")
   @PreAuthorize("hasRole('ADMIN')")
   @Operation(summary = "Obtenir un utilisateur")
   public ResponseEntity<User> getById(@PathVariable UUID tenantId, @PathVariable UUID id) {
      return ResponseEntity.ok(userService.getById(id));
   }

   @DeleteMapping("/{id}")
   @PreAuthorize("hasRole('ADMIN')")
   @Operation(summary = "Supprimer un utilisateur")
   public ResponseEntity<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
      userService.delete(id);
      return ResponseEntity.noContent().build();
   }
}
*/