package talan.pfe.rulengine.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.repositories.RuleRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleScheduler {

    private final RuleRepository ruleRepository;

    @Scheduled(cron = "0 0 0 * * *") // every midnight
    public void applyScheduledChanges() {

        List<Rule> rules = ruleRepository.findAll();

        for (Rule rule : rules) {
            if (rule.getActivationDate() != null &&
                    !rule.getActivationDate().isAfter(LocalDateTime.now())) {

                // Apply enable/disable
                if (rule.getPendingEnabled() != null) {
                    rule.setEnabled(rule.getPendingEnabled());
                    rule.setPendingEnabled(null);
                }

                // Apply updates
                if (Boolean.TRUE.equals(rule.getPendingUpdate())) {
                    rule.setPendingUpdate(false);
                }

                rule.setActivationDate(null);
                ruleRepository.save(rule);
            }
        }
    }
}