package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.response.NotificationResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    PageResponse<NotificationResponse> getAll(Long tenantId, Pageable pageable);
    long getUnreadCount(Long tenantId);
    void markAllAsRead(Long tenantId);
}