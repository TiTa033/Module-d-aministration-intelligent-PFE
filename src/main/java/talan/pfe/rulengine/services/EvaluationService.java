package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.EvaluateRequest;
import talan.pfe.rulengine.dtos.response.EvaluateResponse;
import talan.pfe.rulengine.security.ApiClientPrincipal;

public interface EvaluationService {

    EvaluateResponse evaluate(EvaluateRequest request, ApiClientPrincipal principal);
}
