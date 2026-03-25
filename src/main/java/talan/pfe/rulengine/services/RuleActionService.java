package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;

import java.util.List;
import java.util.UUID;

public interface RuleActionService {

    RuleActionResponse create(UUID ruleSetId,
                              UUID ruleId,
                              UUID tenantId,
                              RuleActionRequest request);

    List<RuleActionResponse> getAll(UUID ruleSetId,
                                    UUID ruleId,
                                    UUID tenantId);

    RuleActionResponse getById(UUID ruleSetId,
                               UUID ruleId,
                               UUID id,
                               UUID tenantId);

    RuleActionResponse update(UUID ruleSetId,
                              UUID ruleId,
                              UUID id,
                              UUID tenantId,
                              RuleActionRequest request);

    void delete(UUID ruleSetId,
                UUID ruleId,
                UUID id,
                UUID tenantId);
}
