package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalAdminDashboardResponse {

    private long totalTenants;
    private long activeTenants;
    private long inactiveTenants;

    private GlobalAdminDashboardChartsResponse charts;
}
