package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.CreateAlertConfigRequest;
import talan.pfe.rulengine.dtos.response.AlertConfigResponse;
import talan.pfe.rulengine.entites.AlertConfig;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AlertConfigRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.AlertConfigService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AlertConfigServiceImpl implements AlertConfigService {

    private final AlertConfigRepository alertConfigRepository;
    private final TenantRepository      tenantRepository;
    private final RuleSetRepository     ruleSetRepository;

    @Override
    public AlertConfigResponse create(CreateAlertConfigRequest request, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        RuleSet ruleSet = null;
        if (request.getRuleSetId() != null) {
            ruleSet = ruleSetRepository.findByIdAndTenantId(request.getRuleSetId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "RuleSet not found: " + request.getRuleSetId()));
        }

        AlertConfig config = AlertConfig.builder()
                .name(request.getName())
                .metric(request.getMetric())
                .conditionType(request.getConditionType())
                .threshold(request.getThreshold())
                .windowHours(request.getWindowHours())
                .enabled(true)
                .tenant(tenant)
                .ruleSet(ruleSet)
                .build();

        return toResponse(alertConfigRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertConfigResponse> getAll(Long tenantId) {
        return alertConfigRepository.findAllByTenantId(tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public AlertConfigResponse toggleEnabled(Long id, Long tenantId) {
        AlertConfig config = alertConfigRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AlertConfig not found: " + id));
        config.setEnabled(!config.isEnabled());
        return toResponse(alertConfigRepository.save(config));
    }

    @Override
    public void delete(Long id, Long tenantId) {
        AlertConfig config = alertConfigRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AlertConfig not found: " + id));
        alertConfigRepository.delete(config);
    }

    private AlertConfigResponse toResponse(AlertConfig c) {
        return AlertConfigResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .metric(c.getMetric())
                .conditionType(c.getConditionType())
                .threshold(c.getThreshold())
                .windowHours(c.getWindowHours())
                .enabled(c.isEnabled())
                .ruleSetId(c.getRuleSet() != null ? c.getRuleSet().getId() : null)
                .ruleSetName(c.getRuleSet() != null ? c.getRuleSet().getName() : null)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
