package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateAlertConfigRequest;
import talan.pfe.rulengine.dtos.response.AlertConfigResponse;

import java.util.List;

public interface AlertConfigService {
    AlertConfigResponse create(CreateAlertConfigRequest request, Long tenantId);
    List<AlertConfigResponse> getAll(Long tenantId);
    AlertConfigResponse toggleEnabled(Long id, Long tenantId);
    void delete(Long id, Long tenantId);
}
