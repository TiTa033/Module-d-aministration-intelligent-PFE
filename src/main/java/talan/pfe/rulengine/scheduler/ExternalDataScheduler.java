package talan.pfe.rulengine.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.services.serviceImpl.ExternalDataCollectionAgent;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalDataScheduler {

    private final ExternalDataCollectionAgent externalDataCollectionAgent;

    // Every day at 07:00 AM
    @Scheduled(cron = "0 0 7 * * *")
    public void dailyCollection() {
        log.info("ExternalDataScheduler: triggering daily financial data collection");
        externalDataCollectionAgent.collectAndAnalyze();
    }
}