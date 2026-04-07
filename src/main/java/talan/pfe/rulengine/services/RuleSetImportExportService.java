package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.RollbackRuleSetRequest;
import talan.pfe.rulengine.dtos.request.RuleSetImportRequest;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.dtos.response.RuleSetValidationResponse;
import talan.pfe.rulengine.dtos.response.RuleSetVersionResponse;

import java.util.List;

public interface RuleSetImportExportService {

    String exportJson(Long ruleSetId, Long tenantId);

    RuleSetResponse importPackage(RuleSetImportRequest request, Long tenantId);

    RuleSetValidationResponse validatePackage(RuleSetImportRequest request, Long tenantId);

    List<RuleSetVersionResponse> listVersions(Long ruleSetId, Long tenantId);

    RuleSetResponse restoreVersion(Long ruleSetId, Long tenantId,
                                   int versionNumber,
                                   RollbackRuleSetRequest request);
}
