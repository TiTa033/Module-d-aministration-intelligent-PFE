package talan.pfe.rulengine.dtos.request;


import lombok.*;
import talan.pfe.rulengine.entites.Tenant;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long totalUsers;

    public static TenantResponse from(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .status(tenant.getStatus().name())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    public static TenantResponse from(Tenant tenant, long totalUsers) {
        TenantResponse response = from(tenant);
        response.setTotalUsers(totalUsers);
        return response;
    }
}