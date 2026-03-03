//package talan.pfe.moduleadministration.controllers;
//
//
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//import talan.pfe.moduleadministration.dtos.request.CreateUserRequest;
//import org.springframework.security.core.userdetails.User;
//import talan.pfe.moduleadministration.services.UserService;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/tenants/{tenantId}/users")
//@RequiredArgsConstructor
//@Tag(name = "Utilisateurs")
//public class UserController {
//
//    private final UserService userService;
//
//    @PostMapping
//    @PreAuthorize("hasRole('ADMIN')")
//    @Operation(summary = "Créer un utilisateur")
//    public ResponseEntity<User> create(@PathVariable Long tenantId, @Valid @RequestBody CreateUserRequest request) {
//        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, tenantId));
//    }
//
//    @GetMapping
//    @PreAuthorize("hasRole('ADMIN')")
//    @Operation(summary = "Lister les utilisateurs du tenant")
//    public ResponseEntity<List<User>> getAll(@PathVariable Long tenantId) {
//        return ResponseEntity.ok(userService.getByTenant(tenantId));
//    }
//
//    @GetMapping("/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
//    @Operation(summary = "Obtenir un utilisateur")
//    public ResponseEntity<User> getById(@PathVariable Long tenantId, @PathVariable Long id) {
//        return ResponseEntity.ok(userService.getById(id));
//    }
//
//    @DeleteMapping("/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
//    @Operation(summary = "Supprimer un utilisateur")
//    public ResponseEntity<Void> delete(@PathVariable Long tenantId, @PathVariable Long id) {
//        userService.delete(id);
//        return ResponseEntity.noContent().build();
//    }
//}
