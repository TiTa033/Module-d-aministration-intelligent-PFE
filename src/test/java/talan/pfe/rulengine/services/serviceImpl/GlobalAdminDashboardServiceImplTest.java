package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.response.GlobalAdminDashboardResponse;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.TenantRepository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalAdminDashboardServiceImpl")
class GlobalAdminDashboardServiceImplTest {

    @Mock TenantRepository tenantRepository;

    @InjectMocks GlobalAdminDashboardServiceImpl service;

    @Test
    @DisplayName("getDashboard() retourne KPIs et données graphiques tenants")
    void getDashboard_returnsTenantMetricsAndCharts() {
        when(tenantRepository.countByStatus(TenantStatus.ACTIVE)).thenReturn(4L);
        when(tenantRepository.countByStatus(TenantStatus.INACTIVE)).thenReturn(1L);
        when(tenantRepository.findDailyCreationsSince(any())).thenReturn(List.of(
                new Object[]{Date.valueOf(LocalDate.now()), 2L},
                new Object[]{Date.valueOf(LocalDate.now().minusDays(1)), 1L}
        ));

        GlobalAdminDashboardResponse result = service.getDashboard(30);

        assertThat(result.getTotalTenants()).isEqualTo(5L);
        assertThat(result.getActiveTenants()).isEqualTo(4L);
        assertThat(result.getInactiveTenants()).isEqualTo(1L);

        assertThat(result.getCharts()).isNotNull();
        assertThat(result.getCharts().getTrendPeriodDays()).isEqualTo(30);
        assertThat(result.getCharts().getTenantsByStatus()).hasSize(2);
        assertThat(result.getCharts().getTenantsByStatus().get(0).getLabel()).isEqualTo("ACTIVE");
        assertThat(result.getCharts().getTenantsByStatus().get(0).getCount()).isEqualTo(4L);

        assertThat(result.getCharts().getTenantCreationsTrend()).hasSize(30);
        long todayCount = result.getCharts().getTenantCreationsTrend().stream()
                .filter(p -> p.getDate().equals(LocalDate.now().toString()))
                .mapToLong(p -> p.getCount())
                .findFirst()
                .orElse(0L);
        assertThat(todayCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("getDashboard() borne trendDays entre 7 et 90")
    void getDashboard_clampsTrendDays() {
        when(tenantRepository.countByStatus(TenantStatus.ACTIVE)).thenReturn(0L);
        when(tenantRepository.countByStatus(TenantStatus.INACTIVE)).thenReturn(0L);
        when(tenantRepository.findDailyCreationsSince(any())).thenReturn(List.of());

        assertThat(service.getDashboard(3).getCharts().getTrendPeriodDays()).isEqualTo(30);
        assertThat(service.getDashboard(14).getCharts().getTrendPeriodDays()).isEqualTo(14);
        assertThat(service.getDashboard(120).getCharts().getTrendPeriodDays()).isEqualTo(90);
        assertThat(service.getDashboard(14).getCharts().getTenantCreationsTrend()).hasSize(14);
    }

    @Test
    @DisplayName("getDashboard() gère l'absence de tenants")
    void getDashboard_emptyPlatform() {
        when(tenantRepository.countByStatus(TenantStatus.ACTIVE)).thenReturn(0L);
        when(tenantRepository.countByStatus(TenantStatus.INACTIVE)).thenReturn(0L);
        when(tenantRepository.findDailyCreationsSince(any())).thenReturn(List.of());

        GlobalAdminDashboardResponse result = service.getDashboard(30);

        assertThat(result.getTotalTenants()).isZero();
        assertThat(result.getCharts().getTenantCreationsTrend())
                .allMatch(p -> p.getCount() == 0L);
    }
}
