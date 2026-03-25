package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;

import java.util.UUID;

public interface RuleSetService {
    RuleSetResponse create(CreateRuleSetRequest request, UUID tenantId);
    RuleSetResponse getById(UUID id, UUID tenantId);
    PageResponse<RuleSetResponse> getAll(UUID tenantId, String search, String status, int page, int size, String sortBy, String sortDir);
    RuleSetResponse update(UUID id, UUID tenantId, UpdateRuleSetRequest request);
    RuleSetResponse activate(UUID id, UUID tenantId);
    RuleSetResponse archive(UUID id, UUID tenantId);
    RuleSetResponse moveToDraft(UUID id, UUID tenantId);
    void delete(UUID id, UUID tenantId);
}
