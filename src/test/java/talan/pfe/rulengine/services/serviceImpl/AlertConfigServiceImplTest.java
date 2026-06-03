package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateAlertConfigRequest;
import talan.pfe.rulengine.dtos.response.AlertConfigResponse;
import talan.pfe.rulengine.entites.AlertConfig;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AlertCondition;
import talan.pfe.rulengine.enums.AlertMetric;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.AlertConfigRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertConfigServiceImpl")
class AlertConfigServiceImplTest {

    @Mock AlertConfigRepository alertConfigRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RuleSetRepository ruleSetRepository;

    @InjectMocks AlertConfigServiceImpl service;

    private Tenant tenant;
    private RuleSet ruleSet;
    private AlertConfig alertConfig;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        ruleSet = RuleSet.builder().id(10L).name("Credit RS").tenant(tenant).build();
        alertConfig = AlertConfig.builder()
                .id(5L)
                .name("High Load Alert")
                .metric(AlertMetric.EVALUATION_COUNT)
                .conditionType(AlertCondition.GREATER_THAN)
                .threshold(100.0)
                .windowHours(1)
                .enabled(true)
                .tenant(tenant)
                .ruleSet(ruleSet)
                .build();
    }

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("should create alert config without ruleSet")
        void create_withoutRuleSet_ok() {
            CreateAlertConfigRequest req = new CreateAlertConfigRequest();
            req.setName("Alert1");
            req.setMetric(AlertMetric.AVG_EXECUTION_MS);
            req.setConditionType(AlertCondition.GREATER_THAN);
            req.setThreshold(500.0);
            req.setWindowHours(24);
            req.setRuleSetId(null);

            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(alertConfigRepository.save(any())).thenReturn(alertConfig);

            AlertConfigResponse result = service.create(req, 1L);

            assertThat(result).isNotNull();
            verify(tenantRepository).findById(1L);
            verify(ruleSetRepository, never()).findByIdAndTenantId(any(), any());
        }

        @Test @DisplayName("should create alert config with ruleSet")
        void create_withRuleSet_ok() {
            CreateAlertConfigRequest req = new CreateAlertConfigRequest();
            req.setName("Alert2");
            req.setMetric(AlertMetric.EVALUATION_COUNT);
            req.setConditionType(AlertCondition.LESS_THAN);
            req.setThreshold(10.0);
            req.setWindowHours(1);
            req.setRuleSetId(10L);

            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(alertConfigRepository.save(any())).thenReturn(alertConfig);

            AlertConfigResponse result = service.create(req, 1L);

            assertThat(result).isNotNull();
            verify(ruleSetRepository).findByIdAndTenantId(10L, 1L);
        }

        @Test @DisplayName("should throw when tenant not found")
        void create_tenantNotFound_throws() {
            CreateAlertConfigRequest req = new CreateAlertConfigRequest();
            req.setName("Alert");
            req.setMetric(AlertMetric.EVALUATION_COUNT);
            req.setConditionType(AlertCondition.GREATER_THAN);
            req.setThreshold(10.0);
            req.setWindowHours(1);

            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(req, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Tenant not found");
        }

        @Test @DisplayName("should throw when ruleSet not found for tenant")
        void create_ruleSetNotFound_throws() {
            CreateAlertConfigRequest req = new CreateAlertConfigRequest();
            req.setName("Alert");
            req.setMetric(AlertMetric.EVALUATION_COUNT);
            req.setConditionType(AlertCondition.GREATER_THAN);
            req.setThreshold(10.0);
            req.setWindowHours(1);
            req.setRuleSetId(999L);

            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(req, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("RuleSet not found");
        }
    }

    @Nested @DisplayName("getAll()")
    class GetAll {

        @Test @DisplayName("should return list of alert configs for tenant")
        void getAll_returnsList() {
            when(alertConfigRepository.findAllByTenantId(1L)).thenReturn(List.of(alertConfig));

            List<AlertConfigResponse> result = service.getAll(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("High Load Alert");
        }

        @Test @DisplayName("should return empty list when no alert configs")
        void getAll_empty() {
            when(alertConfigRepository.findAllByTenantId(1L)).thenReturn(List.of());

            List<AlertConfigResponse> result = service.getAll(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested @DisplayName("toggleEnabled()")
    class ToggleEnabled {

        @Test @DisplayName("should disable an enabled alert config")
        void toggle_enabled_to_disabled() {
            alertConfig.setEnabled(true);
            when(alertConfigRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(alertConfig));
            when(alertConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AlertConfigResponse result = service.toggleEnabled(5L, 1L);

            assertThat(alertConfig.isEnabled()).isFalse();
            assertThat(result).isNotNull();
        }

        @Test @DisplayName("should enable a disabled alert config")
        void toggle_disabled_to_enabled() {
            alertConfig.setEnabled(false);
            when(alertConfigRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(alertConfig));
            when(alertConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.toggleEnabled(5L, 1L);

            assertThat(alertConfig.isEnabled()).isTrue();
        }

        @Test @DisplayName("should throw when alert config not found")
        void toggle_notFound_throws() {
            when(alertConfigRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleEnabled(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("AlertConfig not found");
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {

        @Test @DisplayName("should delete an existing alert config")
        void delete_ok() {
            when(alertConfigRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(alertConfig));

            service.delete(5L, 1L);

            verify(alertConfigRepository).delete(alertConfig);
        }

        @Test @DisplayName("should throw when alert config not found")
        void delete_notFound_throws() {
            when(alertConfigRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("AlertConfig not found");
        }
    }
}
