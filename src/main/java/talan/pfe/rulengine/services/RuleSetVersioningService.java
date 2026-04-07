package talan.pfe.rulengine.services;

public interface RuleSetVersioningService {

    void recordSnapshot(Long ruleSetId, Long tenantId, String changeNote);
}
