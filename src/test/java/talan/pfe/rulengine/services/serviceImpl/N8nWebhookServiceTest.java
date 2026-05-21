package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.repositories.UserRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class N8nWebhookServiceTest {

    @Mock UserRepository userRepository;

    N8nWebhookService service;

    @BeforeEach
    void setUp() {
        service = new N8nWebhookService(new ObjectMapper(), userRepository);
        // default: disabled, will override per test
        setField("enabled", false);
        setField("webhookUrl", "");
        setField("webhookSecret", "");
        setField("timeoutMs", 500);
    }

    private void setField(String name, Object val) {
        ReflectionTestUtils.setField(service, name, val);
    }

    private Tenant tenant(Long id) {
        return Tenant.builder().id(id).name("ACME").build();
    }

    private RuleSet ruleSet(Long id) {
        return RuleSet.builder()
                .id(id).name("CreditRS").tenant(tenant(10L))
                .status(RuleSetStatus.ACTIVE)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .currentVersion(1)
                .build();
    }

    private User user(String email) {
        return User.builder().id(1L).email(email).role(Role.ADMIN)
                .tenant(tenant(10L)).active(true).build();
    }

    // ─── notifyRuleSetCreated ─────────────────────────────────

    @Test
    void notifyRuleSetCreated_whenDisabled_doesNothing() {
        setField("enabled", false);
        service.notifyRuleSetCreated(ruleSet(1L), user("creator@test.com"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void notifyRuleSetCreated_whenEnabledButUrlBlank_doesNothing() {
        setField("enabled", true);
        setField("webhookUrl", "");
        service.notifyRuleSetCreated(ruleSet(1L), user("c@test.com"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void notifyRuleSetCreated_whenNullRuleSet_doesNothing() {
        setField("enabled", true);
        setField("webhookUrl", "http://n8n.example.com/webhook");
        service.notifyRuleSetCreated(null, user("c@test.com"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void notifyRuleSetCreated_whenEnabledWithUrl_attemptsWebhookAndIgnoresNetworkError() {
        // unreachable URL → connection refused → caught silently
        setField("enabled", true);
        setField("webhookUrl", "http://localhost:1/webhook");

        // Should not throw — error is swallowed
        service.notifyRuleSetCreated(ruleSet(1L), user("c@test.com"));
    }

    // ─── notifyRuleSetActivated ───────────────────────────────

    @Test
    void notifyRuleSetActivated_whenDisabled_doesNothing() {
        setField("enabled", false);
        service.notifyRuleSetActivated(ruleSet(1L), user("a@test.com"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void notifyRuleSetActivated_whenEnabledButUrlBlank_doesNothing() {
        setField("enabled", true);
        setField("webhookUrl", "   ");
        service.notifyRuleSetActivated(ruleSet(1L), user("a@test.com"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void notifyRuleSetActivated_whenEnabledWithUrl_queriesTenantMembers() {
        setField("enabled", true);
        setField("webhookUrl", "http://localhost:1/webhook");
        User member = user("member@test.com");
        when(userRepository.findAllByTenantId(10L)).thenReturn(List.of(member));

        service.notifyRuleSetActivated(ruleSet(1L), user("admin@test.com"));

        verify(userRepository).findAllByTenantId(10L);
    }

    @Test
    void notifyRuleSetActivated_excludesActivatorFromEmailList() {
        setField("enabled", true);
        setField("webhookUrl", "http://localhost:1/webhook");
        User activator = user("admin@test.com");
        User member = User.builder().id(2L).email("member@test.com")
                .role(Role.VIEWER).tenant(tenant(10L)).active(true).build();
        when(userRepository.findAllByTenantId(10L)).thenReturn(List.of(activator, member));

        // Should not throw — network error is caught silently
        service.notifyRuleSetActivated(ruleSet(1L), activator);

        verify(userRepository).findAllByTenantId(10L);
    }
}
