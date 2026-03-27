package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import talan.pfe.rulengine.dtos.request.CreateRuleRequest;
import talan.pfe.rulengine.dtos.request.RuleFilterRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleRequest;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.serviceImpl.RuleServiceImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private RuleSetRepository ruleSetRepository;

    private RuleServiceImpl ruleService;

    private Long tenantId;
    private Long ruleSetId;
    private RuleSet ruleSet;
    private Rule rule;

    @BeforeEach
    void setUp() {
        ruleService = new RuleServiceImpl(ruleRepository, ruleSetRepository);

        tenantId = 1L;
        ruleSetId = 10L;

        Tenant tenant = Tenant.builder()
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

        rule = Rule.builder()
                .id(UUID.randomUUID())
                .name("R1")
                .description("d")
                .priority(1)
                .logicOperator(LogicOperator.AND)
                .enabled(true)
                .ruleSet(ruleSet)
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void shouldCreateRule() {
            CreateRuleRequest request = new CreateRuleRequest(
                    "R1", "d", 1, LogicOperator.AND, null);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.existsByNameAndRuleSetId("R1", ruleSetId))
                    .thenReturn(false);
            when(ruleRepository.existsByPriorityAndRuleSetId(1, ruleSetId))
                    .thenReturn(false);
            when(ruleRepository.save(any(Rule.class))).thenReturn(rule);

            RuleResponse response = ruleService.create(ruleSetId, tenantId, request);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("R1");
            verify(ruleRepository).save(any(Rule.class));
        }

        @Test
        void shouldThrowWhenRuleSetArchived() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            CreateRuleRequest request = new CreateRuleRequest(
                    "R1", "d", 1, LogicOperator.AND, null);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> ruleService.create(ruleSetId, tenantId, request))
                    .isInstanceOf(BadRequestException.class);
            verify(ruleRepository, never()).save(any());
        }

        @Test
        void shouldThrowOnDuplicateName() {
            CreateRuleRequest request = new CreateRuleRequest(
                    "R1", "d", 1, LogicOperator.AND, null);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.existsByNameAndRuleSetId("R1", ruleSetId))
                    .thenReturn(true);

            assertThatThrownBy(() -> ruleService.create(ruleSetId, tenantId, request))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void shouldThrowOnDuplicatePriority() {
            CreateRuleRequest request = new CreateRuleRequest(
                    "R1", "d", 1, LogicOperator.AND, null);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.existsByNameAndRuleSetId("R1", ruleSetId))
                    .thenReturn(false);
            when(ruleRepository.existsByPriorityAndRuleSetId(1, ruleSetId))
                    .thenReturn(true);

            assertThatThrownBy(() -> ruleService.create(ruleSetId, tenantId, request))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    @DisplayName("getAll / getById")
    class GetAllGetById {

        @Test
        void getAll_shouldReturnPageResponse() {
            RuleFilterRequest filter = new RuleFilterRequest();
            filter.setSearch("");
            filter.setEnabled("");
            filter.setPage(0);
            filter.setSize(10);
            filter.setSortBy("createdAt");
            filter.setSortDir("desc");

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            Page<Rule> page = new PageImpl<>(
                    List.of(rule),
                    PageRequest.of(0, 10),
                    1
            );
            when(ruleRepository.findAllByRuleSetWithFilters(eq(ruleSetId), anyString(), isNull(), any()))
                    .thenReturn(page);

            PageResponse<RuleResponse> response = ruleService.getAll(ruleSetId, tenantId, filter);

            assertThat(response).isNotNull();
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        void getById_shouldThrowWhenRuleSetNotFound() {
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleService.getById(ruleSetId, 1L, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("enable / disable / delete")
    class EnableDisableDelete {

        @Test
        void enable_shouldThrowWhenAlreadyEnabled() {
            rule.setEnabled(true);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(anyLong(), eq(ruleSetId)))
                    .thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> ruleService.enable(ruleSetId, 1L, tenantId))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void disable_shouldThrowWhenAlreadyDisabled() {
            rule.setEnabled(false);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(anyLong(), eq(ruleSetId)))
                    .thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> ruleService.disable(ruleSetId, 1L, tenantId))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void delete_shouldThrowWhenRuleSetArchived() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> ruleService.delete(ruleSetId, 1L, tenantId))
                    .isInstanceOf(BadRequestException.class);
            verify(ruleRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void update_shouldThrowWhenRuleSetArchived() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            UpdateRuleRequest request = new UpdateRuleRequest(
                    "R1", "d", 1, LogicOperator.AND, null);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> ruleService.update(ruleSetId, 1L, tenantId, request))
                    .isInstanceOf(BadRequestException.class);
            verify(ruleRepository, never()).save(any());
        }
    }
}

