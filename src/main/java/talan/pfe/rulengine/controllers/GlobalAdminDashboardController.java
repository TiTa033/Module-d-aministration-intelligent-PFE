package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import talan.pfe.rulengine.dtos.response.GlobalAdminDashboardResponse;
import talan.pfe.rulengine.services.GlobalAdminDashboardService;

@Tag(name = "Global Admin Dashboard", description = "Vue consolidée de la plateforme pour le Global Admin")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class GlobalAdminDashboardController {

    private final GlobalAdminDashboardService dashboardService;

    @Operation(summary = "Tableau de bord Global Admin : métriques et graphiques tenants uniquement")
    @GetMapping
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<GlobalAdminDashboardResponse> getDashboard(
            @RequestParam(defaultValue = "30") int trendDays) {
        return ResponseEntity.ok(dashboardService.getDashboard(trendDays));
    }
}
