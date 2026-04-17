package talan.pfe.rulengine.services;

import org.springframework.data.domain.Pageable;
import talan.pfe.rulengine.dtos.response.EvaluationDetailResponse;
import talan.pfe.rulengine.dtos.response.EvaluationHistoryResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;

import java.time.LocalDateTime;

public interface EvaluationHistoryService {

    PageResponse<EvaluationHistoryResponse> getAll(
            Long tenantId,
            Long ruleSetId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    EvaluationDetailResponse getById(Long id, Long tenantId);
}