package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateTenantRequest;
import talan.pfe.rulengine.dtos.request.UpdateTenantRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.TenantResponse;

public interface TenantService {

    TenantResponse create(CreateTenantRequest request);

    TenantResponse getById(Long id);

    PageResponse<TenantResponse> getAll(
            String search,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir);

    TenantResponse update(Long id, UpdateTenantRequest request);

    TenantResponse activate(Long id);

    TenantResponse deactivate(Long id);

    void delete(Long id);
}