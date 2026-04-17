package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.response.AuditLogResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.entites.AuditLog;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.repositories.AuditLogRepository;
import talan.pfe.rulengine.repositories.AuditSpecification;
import talan.pfe.rulengine.services.AuditService;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public PageResponse<AuditLogResponse> getAll(Long tenantId,
                                                 AuditAction action,
                                                 String entityType,
                                                 LocalDateTime from,
                                                 LocalDateTime to,
                                                 Pageable pageable) {
        // Force sort by timestamp desc
        Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        Page<AuditLogResponse> page = auditLogRepository
                .findAll(
                        AuditSpecification.withFilters(tenantId, action, entityType, from, to),
                        sortedPageable)
                .map(this::toDto);

        return PageResponse.from(page);
    }

    private AuditLogResponse toDto(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .timestamp(log.getTimestamp())
                .tenantId(log.getTenant() != null ? log.getTenant().getId() : null)
                .tenantName(log.getTenant() != null ? log.getTenant().getName() : null)
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : null)
                .build();
    }
}