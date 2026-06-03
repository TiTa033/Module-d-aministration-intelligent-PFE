package talan.pfe.rulengine.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.entites.AlertConfig;
import talan.pfe.rulengine.enums.AlertCondition;
import talan.pfe.rulengine.enums.AlertMetric;
import talan.pfe.rulengine.enums.NotifType;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.repositories.AlertConfigRepository;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluationScheduler {

    private final AlertConfigRepository       alertConfigRepository;
    private final EvaluationRequestRepository evaluationRequestRepository;
    private final NotificationProducer        notificationProducer;

    /** Évalue toutes les alertes activées toutes les 15 minutes */
    @Scheduled(fixedDelayString = "PT15M")
    public void evaluateAlerts() {
        List<AlertConfig> activeAlerts = alertConfigRepository.findAllByEnabledTrue();
        log.debug("AlertEvaluationScheduler — évaluation de {} alertes", activeAlerts.size());

        for (AlertConfig alert : activeAlerts) {
            try {
                evaluateOne(alert);
            } catch (Exception e) {
                log.error("Erreur lors de l'évaluation de l'alerte id={}: {}", alert.getId(), e.getMessage());
            }
        }
    }

    private void evaluateOne(AlertConfig alert) {
        LocalDateTime to   = LocalDateTime.now();
        LocalDateTime from = to.minusHours(alert.getWindowHours());

        Long   tenantId  = alert.getTenant().getId();
        Long   ruleSetId = alert.getRuleSet() != null ? alert.getRuleSet().getId() : null;

        double currentValue = computeMetric(alert.getMetric(), ruleSetId, tenantId, from, to);
        boolean triggered   = isTriggered(alert.getConditionType(), currentValue, alert.getThreshold());

        if (triggered) {
            String ruleSetLabel = alert.getRuleSet() != null
                    ? " (RuleSet: " + alert.getRuleSet().getName() + ")"
                    : "";

            String message = String.format(
                    "Alerte '%s'%s : %s=%.2f %s seuil=%.2f sur les %dh écoulées.",
                    alert.getName(), ruleSetLabel,
                    alert.getMetric().name(), currentValue,
                    alert.getConditionType() == AlertCondition.GREATER_THAN ? ">" : "<",
                    alert.getThreshold(),
                    alert.getWindowHours());

            notificationProducer.publish(
                    "Seuil d'alerte dépassé : " + alert.getName(),
                    message,
                    NotifType.ALERT_THRESHOLD_EXCEEDED,
                    tenantId,
                    alert.getId(),
                    "AlertConfig");

            log.info("Alerte déclenchée — id={}, valeur={}, seuil={}",
                    alert.getId(), currentValue, alert.getThreshold());
        }
    }

    private double computeMetric(AlertMetric metric, Long ruleSetId,
                                  Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (metric == AlertMetric.EVALUATION_COUNT) {
            Long count = ruleSetId != null
                    ? evaluationRequestRepository.countByRuleSetAndTenantBetween(ruleSetId, tenantId, from, to)
                    : evaluationRequestRepository.countByTenantSince(tenantId, from);
            return count != null ? count.doubleValue() : 0.0;

        } else { // AVG_EXECUTION_MS
            Double avg = ruleSetId != null
                    ? evaluationRequestRepository.avgExecutionMsByRuleSetSince(ruleSetId, tenantId, from)
                    : evaluationRequestRepository.avgExecutionMsByTenantSince(tenantId, from);
            return avg != null ? avg : 0.0;
        }
    }

    private boolean isTriggered(AlertCondition condition, double value, double threshold) {
        return condition == AlertCondition.GREATER_THAN
                ? value > threshold
                : value < threshold;
    }
}
