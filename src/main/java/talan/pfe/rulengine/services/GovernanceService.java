package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.response.GovernanceSummaryResponse;

public interface GovernanceService {
    GovernanceSummaryResponse getSummary(Long tenantId);
}
