package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.response.RuleSetMetricsResponse;

public interface MetricsService {
    RuleSetMetricsResponse getRuleSetMetrics(Long ruleSetId, Long tenantId, int periodDays);
}
