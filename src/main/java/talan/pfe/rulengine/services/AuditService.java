package talan.pfe.rulengine.services;

import org.springframework.data.domain.Pageable;
import talan.pfe.rulengine.dtos.response.AuditLogResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.enums.AuditAction;

import java.time.LocalDateTime;

public interface AuditService {
    PageResponse<AuditLogResponse> getAll(Long tenantId,
                                          AuditAction action,
                                          String entityType,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Pageable pageable);
}