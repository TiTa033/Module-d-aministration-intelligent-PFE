package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.enums.NotifType;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.repositories.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiNotificationService {

    private final NotificationProducer notificationProducer;
    private final MailService mailService;
    private final UserRepository userRepository;

    @Async
    public void notifyAnomalyDetected(Long tenantId, String title,
                                      int criticalCount, int highCount, int totalFindings) {
        String message = String.format(
                "Analyse terminée : %d anomalie(s) détectée(s) dont %d critique(s) et %d élevée(s). Validez dans Détection d'Anomalies IA.",
                totalFindings, criticalCount, highCount);

        notificationProducer.publish(title, message, NotifType.AI_ANOMALY_DETECTED,
                tenantId, null, "AI_INSIGHT");

        List<String> emails = userRepository.findAllByTenantId(tenantId).stream()
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .toList();

        mailService.sendAnomalyDetectedEmail(emails, title, criticalCount, highCount, totalFindings);
    }

    @Async
    public void notifyExternalAnalysisReady(Long tenantId, String period, String insightTitle) {
        String message = "Nouvelles données financières collectées et analysées pour " + period
                + ". Consultez les insights dans Analyse Externe IA.";

        notificationProducer.publish(insightTitle, message, NotifType.AI_EXTERNAL_ANALYSIS_READY,
                tenantId, null, "AI_INSIGHT");

        List<String> emails = userRepository.findAllByTenantId(tenantId).stream()
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .toList();

        mailService.sendExternalAnalysisReadyEmail(emails, period, insightTitle);
    }
}
