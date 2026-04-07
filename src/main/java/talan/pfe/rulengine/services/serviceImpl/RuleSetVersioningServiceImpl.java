package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.RuleSetVersion;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.RuleSetVersionRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.RuleSetSnapshotService;
import talan.pfe.rulengine.services.RuleSetVersioningService;

@Service
@RequiredArgsConstructor
public class RuleSetVersioningServiceImpl implements RuleSetVersioningService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleSetVersionRepository ruleSetVersionRepository;
    private final RuleSetSnapshotService snapshotService;
    private final CurrentUserResolver currentUserResolver;

    @Override
    @Transactional
    public void recordSnapshot(Long ruleSetId, Long tenantId, String changeNote) {
        User user = currentUserResolver.requireUser();
        RuleSet ruleSet = ruleSetRepository
                .findByIdAndTenantIdWithRules(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));

        for (Rule r : ruleSet.getRules()) {
            r.getConditions().size();
            r.getActions().size();
        }

        String json;
        try {
            json = snapshotService.toJson(ruleSet);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize snapshot", e);
        }

        int max = ruleSetVersionRepository.findMaxVersionNumber(ruleSetId);
        int next = max + 1;
        ruleSet.setCurrentVersion(next);

        RuleSetVersion version = RuleSetVersion.builder()
                .versionNumber(next)
                .snapshot(json)
                .changeNote(changeNote)
                .ruleSet(ruleSet)
                .createdBy(user)
                .build();
        ruleSetVersionRepository.save(version);
        ruleSetRepository.save(ruleSet);
    }
}
