package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.response.EvaluationDetailResponse;
import talan.pfe.rulengine.dtos.response.EvaluationHistoryResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.EvaluationHistoryService;

import java.time.LocalDateTime;

@Tag(name = "Evaluation History", description = "Historique paginé des requêtes d'évaluation")
@RestController
@RequestMapping("/api/evaluations/history")
@RequiredArgsConstructor
public class EvaluationHistoryController {

    private final EvaluationHistoryService evaluationHistoryService;
    private final JwtService jwtService;

    @Operation(summary = "Lister l'historique d'évaluation (paginé, filtrable par ruleSet et période)")
    @GetMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<PageResponse<EvaluationHistoryResponse>> getAll(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long ruleSetId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Long tenantId = getTenantId(authHeader);
        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                evaluationHistoryService.getAll(tenantId, ruleSetId, from, to, pageable));
    }

    @Operation(summary = "Détail d'une évaluation par identifiant")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    public ResponseEntity<EvaluationDetailResponse> getById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        Long tenantId = getTenantId(authHeader);
        return ResponseEntity.ok(evaluationHistoryService.getById(id, tenantId));
    }

    private Long getTenantId(String authHeader) {
        String tenantId = jwtService.extractTenantId(authHeader.substring(7));
        return (tenantId != null && !tenantId.equals("null"))
                ? Long.parseLong(tenantId) : null;
    }
}