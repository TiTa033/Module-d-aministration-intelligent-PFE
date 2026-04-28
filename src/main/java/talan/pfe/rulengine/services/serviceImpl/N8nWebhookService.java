package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.repositories.UserRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class N8nWebhookService {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Value("${n8n.webhook.enabled:false}")
    private boolean enabled;

    @Value("${n8n.webhook.url:}")
    private String webhookUrl;

    @Value("${n8n.webhook.secret:}")
    private String webhookSecret;

    @Value("${n8n.webhook.timeout-ms:4000}")
    private int timeoutMs;

    public void notifyRuleSetActivated(RuleSet ruleSet, User activatedBy) {
        if (!enabled || webhookUrl == null || webhookUrl.isBlank() || ruleSet == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            List<String> tenantMemberEmails = resolveTenantMemberEmails(ruleSet, activatedBy);

            payload.put("event", "RULESET_ACTIVATED");
            payload.put("ruleSetId", ruleSet.getId());
            payload.put("ruleSetName", ruleSet.getName());
            payload.put("tenantId", ruleSet.getTenant() != null ? ruleSet.getTenant().getId() : null);
            payload.put("tenantName", ruleSet.getTenant() != null ? ruleSet.getTenant().getName() : null);
            payload.put("status", ruleSet.getStatus() != null ? ruleSet.getStatus().name() : null);
            payload.put("version", ruleSet.getCurrentVersion());
            payload.put("strategy", ruleSet.getEvaluationStrategy() != null ? ruleSet.getEvaluationStrategy().name() : null);
            payload.put("activatedBy", activatedBy != null ? activatedBy.getEmail() : null);
            payload.put("activatedAt", LocalDateTime.now());
            payload.put("tenantMemberEmails", tenantMemberEmails);
            payload.put("tenantMemberEmailsCsv", String.join(",", tenantMemberEmails));

            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json");
            if (webhookSecret != null && !webhookSecret.isBlank()) {
                builder.header("X-Webhook-Secret", webhookSecret);
            }
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("n8n webhook failed with HTTP {} for RuleSet {}", response.statusCode(), ruleSet.getId());
            }
        } catch (Exception e) {
            log.warn("n8n webhook notification skipped due to error: {}", e.getMessage());
        }
    }

    private List<String> resolveTenantMemberEmails(RuleSet ruleSet, User activatedBy) {
        if (ruleSet.getTenant() == null || ruleSet.getTenant().getId() == null) {
            return List.of();
        }
        String activatorEmail = activatedBy != null ? activatedBy.getEmail() : null;
        return userRepository.findAllByTenantId(ruleSet.getTenant().getId())
                .stream()
                .filter(User::isActive)
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .filter(email -> activatorEmail == null || !email.equalsIgnoreCase(activatorEmail))
                .distinct()
                .toList();
    }
}
