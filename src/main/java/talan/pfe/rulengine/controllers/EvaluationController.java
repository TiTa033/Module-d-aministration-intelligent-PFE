package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.EvaluateRequest;
import talan.pfe.rulengine.dtos.response.EvaluateResponse;
import talan.pfe.rulengine.security.ApiClientPrincipal;
import talan.pfe.rulengine.services.EvaluationService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Evaluation", description = "External API evaluation (API key)")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('" + ApiClientPrincipal.ROLE + "')")
    @Operation(summary = "Evaluate a RuleSet",
            description = "Requires X-API-Key. Strategy is taken from the RuleSet assigned to the API key.")
    public ResponseEntity<EvaluateResponse> evaluate(
            @Valid @RequestBody EvaluateRequest request,
            @AuthenticationPrincipal ApiClientPrincipal principal) {
        return ResponseEntity.ok(evaluationService.evaluate(request, principal));
    }
}
