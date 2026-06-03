package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.RuleSetVersionRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.RuleSetSnapshotService;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleSetVersioningServiceImpl")
class RuleSetVersioningServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleSetVersionRepository ruleSetVersionRepository;
    @Mock RuleSetSnapshotService snapshotService;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks RuleSetVersioningServiceImpl service;

    @Test
    @DisplayName("recordSnapshot() crée une version incrémentée")
    void recordSnapshot_success() throws Exception {
        User user = User.builder().id(1L).email("admin@test.com").build();
        RuleSet ruleSet = RuleSet.builder().id(5L).currentVersion(1).rules(new ArrayList<>()).build();

        when(currentUserResolver.requireUser()).thenReturn(user);
        when(ruleSetRepository.findByIdAndTenantIdWithRules(5L, 1L))
                .thenReturn(Optional.of(ruleSet));
        when(snapshotService.toJson(ruleSet)).thenReturn("{\"id\":5}");
        when(ruleSetVersionRepository.findMaxVersionNumber(5L)).thenReturn(1);

        service.recordSnapshot(5L, 1L, "Initial publish");

        verify(ruleSetVersionRepository).save(argThat(v ->
                v.getVersionNumber() == 2 && "Initial publish".equals(v.getChangeNote())));
        verify(ruleSetRepository).save(ruleSet);
    }

    @Test
    @DisplayName("recordSnapshot() lève NotFound si RuleSet absent")
    void recordSnapshot_notFound() {
        when(currentUserResolver.requireUser()).thenReturn(User.builder().id(1L).build());
        when(ruleSetRepository.findByIdAndTenantIdWithRules(99L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordSnapshot(99L, 1L, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
