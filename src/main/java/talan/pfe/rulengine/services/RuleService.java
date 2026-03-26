package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleResponse;

import java.util.List;

public interface RuleService {

    RuleResponse create(Long ruleSetId, Long tenantId, CreateRuleRequest request);

    PageResponse<RuleResponse> getAll(Long ruleSetId, Long tenantId, RuleFilterRequest filter);

    List<RuleResponse> getAllList(Long ruleSetId, Long tenantId);

    RuleResponse getById(Long ruleSetId, Long id, Long tenantId);

    RuleResponse update(Long ruleSetId, Long id, Long tenantId, UpdateRuleRequest request);

    RuleResponse enable(Long ruleSetId, Long id, Long tenantId);

    RuleResponse disable(Long ruleSetId, Long id, Long tenantId);

    void delete(Long ruleSetId, Long id, Long tenantId);
}