package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;

import java.util.List;
import java.util.UUID;

public interface RuleConditionService {

    RuleConditionResponse create(UUID ruleSetId,
                                 UUID ruleId,
                                 UUID tenantId,
                                 RuleConditionRequest request);

    List<RuleConditionResponse> getAll(UUID ruleSetId,
                                       UUID ruleId,
                                       UUID tenantId);

    RuleConditionResponse getById(UUID ruleSetId,
                                  UUID ruleId,
                                  UUID id,
                                  UUID tenantId);

    RuleConditionResponse update(UUID ruleSetId,
                                 UUID ruleId,
                                 UUID id,
                                 UUID tenantId,
                                 RuleConditionRequest request);

    void delete(UUID ruleSetId,
                UUID ruleId,
                UUID id,
                UUID tenantId);
}
