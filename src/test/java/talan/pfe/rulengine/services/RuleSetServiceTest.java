package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.serviceImpl.RuleSetServiceImpl;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleSetServiceTest {

    @Mock
    private RuleSetRepository ruleSetRepository;

    @Mock
    private TenantRepository tenantRepository;

    private RuleSetServiceImpl ruleSetService;

    private Long tenantId;
    private Tenant tenant;
    private RuleSet ruleSet;

    @BeforeEach
    void setUp() {
        ruleSetService = new RuleSetServiceImpl(ruleSetRepository, tenantRepository);

        tenantId = 1L;
        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("BNP")
                .slug("bnp")
                .build();

        ruleSet = RuleSet.builder()
                .id(UUID.randomUUID())
                .name("RS1")
                .description("desc")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .status(RuleSetStatus.DRAFT)
                .currentVersion(1)
                .tenant(tenant)
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void shouldCreateRuleSet() {
            CreateRuleSetRequest request = new CreateRuleSetRequest(
                    "RS1", "desc", EvaluationStrategy.FIRST_MATCH);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.existsByNameAndTenantId("RS1", tenantId)).thenReturn(false);
            when(ruleSetRepository.save(any(RuleSet.class))).thenReturn(ruleSet);

            RuleSetResponse response = ruleSetService.create(request, tenantId);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("RS1");
            assertThat(response.getTenantName()).isEqualTo("BNP");
            verify(ruleSetRepository).save(any(RuleSet.class));
        }

        @Test
        void shouldThrowWhenTenantNotFound() {
            CreateRuleSetRequest request = new CreateRuleSetRequest(
                    "RS1", "desc", EvaluationStrategy.FIRST_MATCH);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleSetService.create(request, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(ruleSetRepository, never()).save(any());
        }

        @Test
        void shouldThrowOnDuplicateNameInTenant() {
            CreateRuleSetRequest request = new CreateRuleSetRequest(
                    "RS1", "desc", EvaluationStrategy.FIRST_MATCH);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.existsByNameAndTenantId("RS1", tenantId)).thenReturn(true);

            assertThatThrownBy(() -> ruleSetService.create(request, tenantId))
                    .isInstanceOf(ConflictException.class);
            verify(ruleSetRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void shouldReturnRuleSet() {
            Long ruleSetId = 10L;
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            RuleSetResponse response = ruleSetService.getById(ruleSetId, tenantId);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("RS1");
        }

        @Test
        void shouldThrowWhenNotFound() {
            Long ruleSetId = 10L;
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleSetService.getById(ruleSetId, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void shouldUpdateRuleSet() {
            Long ruleSetId = 10L;
            UpdateRuleSetRequest request = new UpdateRuleSetRequest(
                    "RS1-updated", "new", EvaluationStrategy.ALL_MATCH);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleSetRepository.existsByNameAndTenantIdAndIdNot(
                    "RS1-updated", tenantId, ruleSetId)).thenReturn(false);
            when(ruleSetRepository.save(any(RuleSet.class))).thenReturn(ruleSet);

            RuleSetResponse response = ruleSetService.update(ruleSetId, tenantId, request);

            assertThat(response).isNotNull();
            verify(ruleSetRepository).save(ruleSet);
        }

        @Test
        void shouldThrowWhenArchived() {
            Long ruleSetId = 10L;
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            UpdateRuleSetRequest request = new UpdateRuleSetRequest(
                    "RS1-updated", "new", EvaluationStrategy.ALL_MATCH);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> ruleSetService.update(ruleSetId, tenantId, request))
                    .isInstanceOf(BadRequestException.class);
            verify(ruleSetRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("activate / delete")
    class ActivateDelete {

        @Test
        void activate_shouldFailWhenNoRules() {
            Long ruleSetId = 10L;
            ruleSet.getRules().clear();
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> ruleSetService.activate(ruleSetId, tenantId))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void delete_shouldFailWhenActive() {
            Long ruleSetId = 10L;
            ruleSet.setStatus(RuleSetStatus.ACTIVE);
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> ruleSetService.delete(ruleSetId, tenantId))
                    .isInstanceOf(BadRequestException.class);
            verify(ruleSetRepository, never()).delete(any());
        }
    }
}

