package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.response.GlobalAdminDashboardResponse;

public interface GlobalAdminDashboardService {

    GlobalAdminDashboardResponse getDashboard(int trendPeriodDays);
}
