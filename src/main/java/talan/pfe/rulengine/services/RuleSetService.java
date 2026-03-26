package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;

public interface RuleSetService {

    RuleSetResponse create(CreateRuleSetRequest request, Long tenantId);

    RuleSetResponse getById(Long id, Long tenantId);

    PageResponse<RuleSetResponse> getAll(
            Long tenantId,
            String search,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir);

    RuleSetResponse update(Long id, Long tenantId, UpdateRuleSetRequest request);

    RuleSetResponse activate(Long id, Long tenantId);

    RuleSetResponse archive(Long id, Long tenantId);

    RuleSetResponse moveToDraft(Long id, Long tenantId);

    void delete(Long id, Long tenantId);
}