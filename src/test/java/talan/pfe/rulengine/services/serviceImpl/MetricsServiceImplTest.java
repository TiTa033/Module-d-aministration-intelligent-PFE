package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.response.RuleSetMetricsResponse;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsServiceImpl")
class MetricsServiceImplTest {

    @Mock EvaluationRequestRepository evaluationRequestRepository;
    @Mock RuleSetRepository ruleSetRepository;

    @InjectMocks MetricsServiceImpl service;

    private Tenant tenant;
    private RuleSet ruleSet;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        ruleSet = RuleSet.builder()
                .id(10L).name("Credit RS")
                .status(RuleSetStatus.ACTIVE)
                .tenant(tenant)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .rules(new ArrayList<>())
                .build();
    }

    @Nested @DisplayName("getRuleSetMetrics()")
    class GetRuleSetMetrics {

        @Test @DisplayName("should return metrics with data")
        void getMetrics_returnsPopulatedResponse() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(evaluationRequestRepository.countByRuleSetAndTenantSince(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(200L);
            when(evaluationRequestRepository.avgExecutionMsByRuleSetSince(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(85.0);

            Object[] row = {"2025-01-01", 50L, 90.0};
            List<Object[]> dailyRows = Collections.singletonList(row);
            when(evaluationRequestRepository.findDailyStatsByRuleSet(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(dailyRows);

            RuleSetMetricsResponse result = service.getRuleSetMetrics(10L, 1L, 30);

            assertThat(result).isNotNull();
            assertThat(result.getRuleSetId()).isEqualTo(10L);
            assertThat(result.getRuleSetName()).isEqualTo("Credit RS");
            assertThat(result.getPeriodDays()).isEqualTo(30);
            assertThat(result.getTotalEvaluations()).isEqualTo(200L);
            assertThat(result.getAvgExecutionMs()).isEqualTo(85.0);
            assertThat(result.getDailyStats()).hasSize(1);
            assertThat(result.getDailyStats().get(0).getDate()).isEqualTo("2025-01-01");
        }

        @Test @DisplayName("should return defaults when repository returns null")
        void getMetrics_nullValues_returnsDefaults() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(evaluationRequestRepository.countByRuleSetAndTenantSince(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(null);
            when(evaluationRequestRepository.avgExecutionMsByRuleSetSince(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(null);
            when(evaluationRequestRepository.findDailyStatsByRuleSet(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(List.of());

            RuleSetMetricsResponse result = service.getRuleSetMetrics(10L, 1L, 7);

            assertThat(result.getTotalEvaluations()).isEqualTo(0L);
            assertThat(result.getAvgExecutionMs()).isEqualTo(0.0);
            assertThat(result.getDailyStats()).isEmpty();
        }

        @Test @DisplayName("should handle null values in daily stat rows")
        void getMetrics_nullDailyStatRows_usesDefaults() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(evaluationRequestRepository.countByRuleSetAndTenantSince(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(5L);
            when(evaluationRequestRepository.avgExecutionMsByRuleSetSince(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(50.0);

            Object[] rowWithNulls = {null, null, null};
            List<Object[]> nullRows = Collections.singletonList(rowWithNulls);
            when(evaluationRequestRepository.findDailyStatsByRuleSet(
                    eq(10L), eq(1L), any(LocalDateTime.class))).thenReturn(nullRows);

            RuleSetMetricsResponse result = service.getRuleSetMetrics(10L, 1L, 90);

            assertThat(result.getDailyStats()).hasSize(1);
            assertThat(result.getDailyStats().get(0).getDate()).isNull();
            assertThat(result.getDailyStats().get(0).getCount()).isEqualTo(0L);
            assertThat(result.getDailyStats().get(0).getAvgExecutionMs()).isEqualTo(0.0);
        }

        @Test @DisplayName("should throw when ruleSet not found")
        void getMetrics_ruleSetNotFound_throws() {
            when(ruleSetRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRuleSetMetrics(99L, 1L, 30))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("RuleSet not found");
        }
    }
}
