package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;


import java.util.List;

public interface RuleConditionService {
    RuleConditionResponse create(Long ruleSetId, Long ruleId, Long tenantId, RuleConditionRequest request);
    List<RuleConditionResponse> getAll(Long ruleSetId, Long ruleId, Long tenantId);
    RuleConditionResponse getById(Long ruleSetId, Long ruleId, Long id, Long tenantId);
    RuleConditionResponse update(Long ruleSetId, Long ruleId, Long id, Long tenantId, RuleConditionRequest request);
    void delete(Long ruleSetId, Long ruleId, Long id, Long tenantId);
}
