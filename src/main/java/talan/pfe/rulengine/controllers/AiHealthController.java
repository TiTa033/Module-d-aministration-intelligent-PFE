package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
@Tag(name = "AI Health", description = "Vérification de l'état des agents IA et de la connexion LLM")
@Slf4j
public class AiHealthController {

    private final LlmClient llmClient;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.model}")
    private String model;

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN')")
    @Operation(summary = "Tester la connexion LLM et l'état de tous les agents IA")
    public ResponseEntity<Map<String, Object>> health() {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("baseUrl", baseUrl);
        result.put("model", model);

        long start = System.currentTimeMillis();
        String llmStatus;
        String llmResponse = null;
        String llmError = null;

        try {
            llmResponse = llmClient.generate(
                    "Tu es un assistant de test. Réponds uniquement avec le mot OK.",
                    "Réponds uniquement avec le mot OK."
            );
            llmStatus = "OK";
        } catch (Exception e) {
            llmStatus = "ERROR";
            llmError = e.getMessage();
            log.warn("AI health check failed: {}", e.getMessage());
        }

        long responseTime = System.currentTimeMillis() - start;

        result.put("llm", Map.of(
                "status", llmStatus,
                "responseTimeMs", responseTime,
                "response", llmResponse != null ? llmResponse.trim() : "",
                "error", llmError != null ? llmError : ""
        ));

        result.put("agents", List.of(
                agentInfo("ChatService",                "LlmClient.chat()"),
                agentInfo("RuleSetDocumentationAgent",  "LlmClient.generate()"),
                agentInfo("ExternalDataCollectionAgent","LlmClient.generate()"),
                agentInfo("PlatformGuideAgent",         "LlmClient.generate()"),
                agentInfo("RuleSimulationServiceImpl",  "LlmClient.generate()")
        ));

        result.put("overall", "OK".equals(llmStatus) ? "OK" : "DEGRADED");

        return ResponseEntity.ok(result);
    }

    private Map<String, String> agentInfo(String name, String method) {
        return Map.of("agent", name, "method", method);
    }
}
