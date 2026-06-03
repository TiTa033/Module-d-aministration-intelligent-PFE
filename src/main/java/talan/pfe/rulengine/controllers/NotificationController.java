package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.response.NotificationResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.NotificationService;

import java.util.Map;

@Tag(name = "Notifications", description = "Gestion des notifications utilisateur du tenant")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtService jwtService;

    @Operation(summary = "Lister les notifications du tenant (paginé)")
    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<PageResponse<NotificationResponse>> getAll(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                notificationService.getAll(
                        getTenantId(authHeader),
                        PageRequest.of(page, size)));
    }

    @Operation(summary = "Nombre de notifications non lues")
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("Authorization") String authHeader) {

        long count = notificationService.getUnreadCount(getTenantId(authHeader));
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "Marquer toutes les notifications comme lues")
    @PatchMapping("/mark-all-read")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("Authorization") String authHeader) {

        notificationService.markAllAsRead(getTenantId(authHeader));
        return ResponseEntity.noContent().build();
    }

    private Long getTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId) : null;
    }
}