package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.RuleSetVersionRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.RuleSetSnapshotService;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleSetVersioningServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleSetVersionRepository ruleSetVersionRepository;
    @Mock RuleSetSnapshotService snapshotService;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks RuleSetVersioningServiceImpl service;

    private User user() {
        return User.builder().id(1L).email("admin@test.com").role(Role.ADMIN)
                .tenant(Tenant.builder().id(10L).name("Acme").build())
                .active(true).build();
    }

    private RuleSet ruleSet(Long id) {
        return RuleSet.builder()
                .id(id).name("CreditRS")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .status(RuleSetStatus.ACTIVE)
                .currentVersion(1)
                .rules(new ArrayList<>())
                .build();
    }

    // ─── recordSnapshot ───────────────────────────────────────

    @Test
    void recordSnapshot_whenRuleSetNotFound_throwsResourceNotFound() {
        when(currentUserResolver.requireUser()).thenReturn(user());
        when(ruleSetRepository.findByIdAndTenantIdWithRules(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordSnapshot(99L, 10L, "initial"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void recordSnapshot_savesVersionAndUpdatesRuleSet() throws JsonProcessingException {
        User u = user();
        RuleSet rs = ruleSet(1L);
        when(currentUserResolver.requireUser()).thenReturn(u);
        when(ruleSetRepository.findByIdAndTenantIdWithRules(1L, 10L)).thenReturn(Optional.of(rs));
        when(snapshotService.toJson(rs)).thenReturn("{\"name\":\"CreditRS\"}");
        when(ruleSetVersionRepository.findMaxVersionNumber(1L)).thenReturn(2);
        when(ruleSetVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ruleSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordSnapshot(1L, 10L, "v3 change");

        ArgumentCaptor<RuleSetVersion> captor = ArgumentCaptor.forClass(RuleSetVersion.class);
        verify(ruleSetVersionRepository).save(captor.capture());
        RuleSetVersion saved = captor.getValue();
        assertThat(saved.getVersionNumber()).isEqualTo(3);
        assertThat(saved.getSnapshot()).isEqualTo("{\"name\":\"CreditRS\"}");
        assertThat(saved.getChangeNote()).isEqualTo("v3 change");
        assertThat(saved.getCreatedBy()).isEqualTo(u);
        assertThat(rs.getCurrentVersion()).isEqualTo(3);
    }

    @Test
    void recordSnapshot_whenSnapshotFails_throwsIllegalState() throws JsonProcessingException {
        User u = user();
        RuleSet rs = ruleSet(1L);
        when(currentUserResolver.requireUser()).thenReturn(u);
        when(ruleSetRepository.findByIdAndTenantIdWithRules(1L, 10L)).thenReturn(Optional.of(rs));
        when(snapshotService.toJson(rs)).thenThrow(new JsonProcessingException("bad") {});

        assertThatThrownBy(() -> service.recordSnapshot(1L, 10L, "note"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serialize");
    }
}
