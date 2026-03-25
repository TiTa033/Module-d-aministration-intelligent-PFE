package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateTenantRequest;
import talan.pfe.rulengine.dtos.request.UpdateTenantRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.TenantResponse;

import java.util.UUID;

public interface TenantService {
    TenantResponse create(CreateTenantRequest request);
    TenantResponse getById(UUID id);
    PageResponse<TenantResponse> getAll(String search, String status, int page, int size, String sortBy, String sortDir);
    TenantResponse update(UUID id, UpdateTenantRequest request);
    TenantResponse activate(UUID id);
    TenantResponse deactivate(UUID id);
    void delete(UUID id);
}
