package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateApiKeyRequest;
import talan.pfe.rulengine.dtos.response.ApiKeyCreatedResponse;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;

import java.util.List;

public interface ApiKeyService {

    ApiKeyCreatedResponse generate(CreateApiKeyRequest request, Long tenantId);
    List<ApiKeyResponse> getAll(Long tenantId);
    ApiKeyResponse getById(Long id, Long tenantId);
    ApiKeyResponse revoke(Long id, Long tenantId);
    ApiKeyCreatedResponse regenerate(Long id, Long tenantId);
    void delete(Long id, Long tenantId);
}