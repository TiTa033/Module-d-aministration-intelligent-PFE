package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleAction;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;

import java.util.List;

public interface RuleActionService {
    RuleActionResponse create(Long ruleSetId, Long ruleId, Long tenantId, RuleActionRequest request);
    List<RuleActionResponse> getAll(Long ruleSetId, Long ruleId, Long tenantId);
    RuleActionResponse getById(Long ruleSetId, Long ruleId, Long id, Long tenantId);
    RuleActionResponse update(Long ruleSetId, Long ruleId, Long id, Long tenantId, RuleActionRequest request);
    void delete(Long ruleSetId, Long ruleId, Long id, Long tenantId);

}
