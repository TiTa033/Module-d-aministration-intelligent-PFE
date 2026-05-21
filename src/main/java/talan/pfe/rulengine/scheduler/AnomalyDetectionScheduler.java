package talan.pfe.rulengine.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.services.serviceImpl.AnomalyDetectionAgent;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionScheduler {

    private final AnomalyDetectionAgent anomalyDetectionAgent;

    public void runOnRuleSetActivation(Long tenantId) {
        log.info("AnomalyDetectionScheduler: triggering anomaly detection for tenant {} after ruleset activation", tenantId);
        anomalyDetectionAgent.analyzeForTenant(tenantId);
    }
}
