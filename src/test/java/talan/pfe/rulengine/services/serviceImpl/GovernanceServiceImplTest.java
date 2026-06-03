package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import talan.pfe.rulengine.dtos.response.GovernanceSummaryResponse;
import talan.pfe.rulengine.entites.AlertConfig;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.repositories.AlertConfigRepository;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.NotificationRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GovernanceServiceImpl")
class GovernanceServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock EvaluationRequestRepository evaluationRequestRepository;
    @Mock AlertConfigRepository alertConfigRepository;
    @Mock NotificationRepository notificationRepository;

    @InjectMocks GovernanceServiceImpl service;

    @Test
    @DisplayName("getSummary() should return populated governance summary")
    void getSummary_returnsPopulatedResponse() {
        Long tenantId = 1L;

        Page<RuleSet> activePage = new PageImpl<>(List.of(RuleSet.builder().id(1L).build()));
        when(ruleSetRepository.findAllByTenantWithFilters(
                eq(tenantId), eq(""), eq(RuleSetStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(activePage);

        when(evaluationRequestRepository.countByTenantSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(42L);

        when(evaluationRequestRepository.avgExecutionMsByTenantSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(150.5);

        AlertConfig ac = AlertConfig.builder().id(1L).enabled(true).build();
        when(alertConfigRepository.findAllByTenantIdAndEnabledTrue(tenantId))
                .thenReturn(List.of(ac));

        when(notificationRepository.countByTenantIdAndReadFalse(tenantId)).thenReturn(5L);

        Object[] row = {1L, "Credit RS", 10L};
        List<Object[]> topRows = Collections.singletonList(row);
        when(evaluationRequestRepository.findTopRuleSetsByTenant(
                eq(tenantId), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(topRows);
        when(evaluationRequestRepository.findDailyEvaluationsByTenant(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(evaluationRequestRepository.findStrategyCountsByTenant(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(List.of());

        GovernanceSummaryResponse result = service.getSummary(tenantId);

        assertThat(result).isNotNull();
        assertThat(result.getActiveRuleSets()).isEqualTo(1L);
        assertThat(result.getTotalEvaluationsToday()).isEqualTo(42L);
        assertThat(result.getAvgExecutionMsToday()).isEqualTo(150.5);
        assertThat(result.getActiveAlerts()).isEqualTo(1L);
        assertThat(result.getUnreadNotifications()).isEqualTo(5L);
        assertThat(result.getTopRuleSets()).hasSize(1);
        assertThat(result.getTopRuleSets().get(0).getRuleSetName()).isEqualTo("Credit RS");
        assertThat(result.getCharts()).isNotNull();
        assertThat(result.getCharts().getEvaluationsTrend()).hasSize(7);
        assertThat(result.getCharts().getStrategiesByType()).hasSize(3);
    }

    @Test
    @DisplayName("getSummary() should handle null values from repositories gracefully")
    void getSummary_nullValues_returnsDefaults() {
        Long tenantId = 2L;

        when(ruleSetRepository.findAllByTenantWithFilters(
                eq(tenantId), eq(""), eq(RuleSetStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        when(evaluationRequestRepository.countByTenantSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(null);

        when(evaluationRequestRepository.avgExecutionMsByTenantSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(null);

        when(alertConfigRepository.findAllByTenantIdAndEnabledTrue(tenantId)).thenReturn(List.of());

        when(notificationRepository.countByTenantIdAndReadFalse(tenantId)).thenReturn(0L);

        when(evaluationRequestRepository.findTopRuleSetsByTenant(
                eq(tenantId), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        GovernanceSummaryResponse result = service.getSummary(tenantId);

        assertThat(result.getTotalEvaluationsToday()).isEqualTo(0L);
        assertThat(result.getAvgExecutionMsToday()).isEqualTo(0.0);
        assertThat(result.getTopRuleSets()).isEmpty();
    }

    @Test
    @DisplayName("getSummary() should handle null row values in top ruleSets")
    void getSummary_nullRowValues_returnsDefaults() {
        Long tenantId = 3L;

        when(ruleSetRepository.findAllByTenantWithFilters(
                eq(tenantId), eq(""), eq(RuleSetStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        when(evaluationRequestRepository.countByTenantSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(5L);

        when(evaluationRequestRepository.avgExecutionMsByTenantSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(100.0);

        when(alertConfigRepository.findAllByTenantIdAndEnabledTrue(tenantId)).thenReturn(List.of());
        when(notificationRepository.countByTenantIdAndReadFalse(tenantId)).thenReturn(0L);

        Object[] rowWithNulls = {null, null, null};
        List<Object[]> nullRows = Collections.singletonList(rowWithNulls);
        when(evaluationRequestRepository.findTopRuleSetsByTenant(
                eq(tenantId), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(nullRows);
        when(evaluationRequestRepository.findDailyEvaluationsByTenant(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(evaluationRequestRepository.findStrategyCountsByTenant(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(List.of());

        GovernanceSummaryResponse result = service.getSummary(tenantId);

        assertThat(result.getTopRuleSets()).hasSize(1);
        assertThat(result.getTopRuleSets().get(0).getRuleSetId()).isNull();
        assertThat(result.getTopRuleSets().get(0).getRuleSetName()).isNull();
        assertThat(result.getTopRuleSets().get(0).getEvaluationCount()).isEqualTo(0L);
    }
}
