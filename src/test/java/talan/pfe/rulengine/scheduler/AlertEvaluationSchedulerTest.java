package talan.pfe.rulengine.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.entites.AlertConfig;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AlertCondition;
import talan.pfe.rulengine.enums.AlertMetric;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.repositories.AlertConfigRepository;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertEvaluationScheduler")
class AlertEvaluationSchedulerTest {

    @Mock AlertConfigRepository alertConfigRepository;
    @Mock EvaluationRequestRepository evaluationRequestRepository;
    @Mock NotificationProducer notificationProducer;

    @InjectMocks AlertEvaluationScheduler scheduler;

    @Test
    @DisplayName("evaluateAlerts() publie une notification si seuil dépassé")
    void evaluateAlerts_triggersNotification() {
        Tenant tenant = Tenant.builder().id(1L).name("T1").build();
        AlertConfig alert = AlertConfig.builder()
                .id(10L)
                .name("High load")
                .metric(AlertMetric.EVALUATION_COUNT)
                .conditionType(AlertCondition.GREATER_THAN)
                .threshold(5.0)
                .windowHours(1)
                .enabled(true)
                .tenant(tenant)
                .ruleSet(null)
                .build();

        when(alertConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(alert));
        when(evaluationRequestRepository.countByTenantSince(eq(1L), any())).thenReturn(10L);

        scheduler.evaluateAlerts();

        verify(notificationProducer).publish(
                contains("High load"),
                anyString(),
                any(),
                eq(1L),
                eq(10L),
                eq("AlertConfig"));
    }

    @Test
    @DisplayName("evaluateAlerts() ignore si seuil non atteint")
    void evaluateAlerts_noTrigger() {
        Tenant tenant = Tenant.builder().id(1L).build();
        AlertConfig alert = AlertConfig.builder()
                .id(11L)
                .name("Low")
                .metric(AlertMetric.EVALUATION_COUNT)
                .conditionType(AlertCondition.GREATER_THAN)
                .threshold(100.0)
                .windowHours(24)
                .tenant(tenant)
                .build();

        when(alertConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(alert));
        when(evaluationRequestRepository.countByTenantSince(eq(1L), any())).thenReturn(2L);

        scheduler.evaluateAlerts();

        verify(notificationProducer, never()).publish(any(), any(), any(), any(), any(), any());
    }
}
