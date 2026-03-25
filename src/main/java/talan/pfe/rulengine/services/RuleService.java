package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateRuleRequest;
import talan.pfe.rulengine.dtos.request.RuleFilterRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleResponse;

import java.util.List;
import java.util.UUID;

public interface RuleService {
    RuleResponse create(UUID ruleSetId, UUID tenantId, CreateRuleRequest request);
    PageResponse<RuleResponse> getAll(UUID ruleSetId, UUID tenantId, RuleFilterRequest filter);
    List<RuleResponse> getAllList(UUID ruleSetId, UUID tenantId);
    RuleResponse getById(UUID ruleSetId, UUID id, UUID tenantId);
    RuleResponse update(UUID ruleSetId, UUID id, UUID tenantId, UpdateRuleRequest request);
    RuleResponse enable(UUID ruleSetId, UUID id, UUID tenantId);
    RuleResponse disable(UUID ruleSetId, UUID id, UUID tenantId);
    void delete(UUID ruleSetId, UUID id, UUID tenantId);
}
