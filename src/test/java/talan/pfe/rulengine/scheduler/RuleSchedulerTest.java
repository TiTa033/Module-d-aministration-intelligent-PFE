package talan.pfe.rulengine.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.repositories.RuleRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleScheduler")
class RuleSchedulerTest {

    @Mock RuleRepository ruleRepository;

    @InjectMocks RuleScheduler scheduler;

    @Test
    @DisplayName("applyScheduledChanges() applique pendingEnabled")
    void applyScheduledChanges() {
        Rule rule = Rule.builder()
                .id(1L)
                .enabled(false)
                .pendingEnabled(true)
                .activationDate(LocalDateTime.now().minusMinutes(1))
                .build();
        when(ruleRepository.findAll()).thenReturn(List.of(rule));

        scheduler.applyScheduledChanges();

        verify(ruleRepository).save(rule);
        assert rule.isEnabled();
        assert rule.getPendingEnabled() == null;
        assert rule.getActivationDate() == null;
    }
}
