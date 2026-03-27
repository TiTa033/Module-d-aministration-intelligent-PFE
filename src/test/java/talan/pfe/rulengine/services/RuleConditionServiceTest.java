package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleCondition;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.mappers.RuleConditionMapper;
import talan.pfe.rulengine.repositories.RuleConditionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.serviceImpl.RuleConditionServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleConditionServiceTest {

    @Mock
    private RuleSetRepository ruleSetRepository;

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private RuleConditionRepository ruleConditionRepository;

    @Mock
    private RuleConditionMapper ruleConditionMapper;

    @InjectMocks
    private
    RuleConditionServiceImpl ruleConditionService;

    private Long tenantId;
    private Long ruleSetId;
    private Long ruleId;
    private RuleSet ruleSet;
    private Rule rule;

    @BeforeEach
    void setUp() {
        tenantId = 1L;
        ruleSetId = 10L;
        ruleId = 100L;

        ruleSet = RuleSet.builder()
                .id(ruleSetId)
                .name("Test RuleSet")
                .description("desc")
                .evaluationStrategy(null)
                .status(RuleSetStatus.DRAFT)
                .currentVersion(1)
                .createdAt(LocalDateTime.now())
                .build();

        rule = Rule.builder()
                .id(ruleId)
                .name("Age >= 18")
                .priority(1)
                .enabled(true)
                .logicOperator(LogicOperator.AND)
                .ruleSet(ruleSet)
                .build();

        lenient().when(ruleConditionMapper.toDto(any(RuleCondition.class)))
                .thenAnswer(invocation -> RuleConditionResponse.from(invocation.getArgument(0)));
        lenient().when(ruleConditionMapper.toDtoList(anyList()))
                .thenAnswer(invocation -> {
                    List<RuleCondition> conditions = invocation.getArgument(0);
                    return conditions.stream().map(RuleConditionResponse::from).toList();
                });
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("doit créer une RuleCondition avec succès")
        void shouldCreateConditionSuccessfully() {
            RuleConditionRequest request = new RuleConditionRequest();
            request.setField("age");
            request.setOperator(Operator.GREATER_OR_EQUAL);
            request.setValue("18");
            request.setValueType(DataType.NUMBER);

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleCondition saved = RuleCondition.builder()
                    .id(1000L)
                    .field(request.getField())
                    .operator(request.getOperator())
                    .value(request.getValue())
                    .valueType(request.getValueType())
                    .rule(rule)
                    .build();
            when(ruleConditionRepository.save(any(RuleCondition.class)))
                    .thenReturn(saved);

            RuleConditionResponse response =
                    ruleConditionService.create(ruleSetId, ruleId, tenantId, request);

            assertThat(response).isNotNull();
            assertThat(response.getField()).isEqualTo("age");
            assertThat(response.getOperator()).isEqualTo(Operator.GREATER_OR_EQUAL);
            assertThat(response.getValueType()).isEqualTo(DataType.NUMBER);
            verify(ruleConditionRepository).save(any(RuleCondition.class));
        }

        @Test
        @DisplayName("doit refuser la création si le RuleSet est archivé")
        void shouldFailWhenRuleSetArchived() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleConditionRequest request = new RuleConditionRequest();
            request.setField("age");
            request.setOperator(Operator.EQUALS);
            request.setValue("18");
            request.setValueType(DataType.NUMBER);

            assertThatThrownBy(() ->
                    ruleConditionService.create(ruleSetId, ruleId, tenantId, request))
                    .isInstanceOf(BadRequestException.class);
            verify(ruleConditionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("doit retourner toutes les conditions de la règle")
        void shouldReturnAllConditions() {
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleCondition cond = RuleCondition.builder()
                    .id(1001L)
                    .field("age")
                    .operator(Operator.GREATER_OR_EQUAL)
                    .value("18")
                    .valueType(DataType.NUMBER)
                    .rule(rule)
                    .build();
            when(ruleConditionRepository.findAllByRuleIdOrderByIdAsc(ruleId))
                    .thenReturn(List.of(cond));

            List<RuleConditionResponse> result =
                    ruleConditionService.getAll(ruleSetId, ruleId, tenantId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getField()).isEqualTo("age");
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("doit retourner une condition par id")
        void shouldReturnConditionById() {
            Long conditionId = 2000L;
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleCondition cond = RuleCondition.builder()
                    .id(conditionId)
                    .field("age")
                    .operator(Operator.EQUALS)
                    .value("30")
                    .valueType(DataType.NUMBER)
                    .rule(rule)
                    .build();
            when(ruleConditionRepository.findById(conditionId))
                    .thenReturn(Optional.of(cond));

            RuleConditionResponse response =
                    ruleConditionService.getById(ruleSetId, ruleId, conditionId, tenantId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(conditionId);
            assertThat(response.getField()).isEqualTo("age");
        }

        @Test
        @DisplayName("doit lever une exception si la condition n'appartient pas à la règle")
        void shouldThrowWhenConditionNotInRule() {
            Long conditionId = 2001L;
            Long anotherRuleId = 2002L;

            Rule anotherRule = Rule.builder()
                    .id(anotherRuleId)
                    .ruleSet(ruleSet)
                    .build();

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleCondition cond = RuleCondition.builder()
                    .id(conditionId)
                    .field("age")
                    .operator(Operator.EQUALS)
                    .value("30")
                    .valueType(DataType.NUMBER)
                    .rule(anotherRule)
                    .build();
            when(ruleConditionRepository.findById(conditionId))
                    .thenReturn(Optional.of(cond));

            assertThatThrownBy(() ->
                    ruleConditionService.getById(ruleSetId, ruleId, conditionId, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("doit mettre à jour une condition")
        void shouldUpdateCondition() {
            Long conditionId = 3000L;
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleCondition cond = RuleCondition.builder()
                    .id(conditionId)
                    .field("age")
                    .operator(Operator.EQUALS)
                    .value("18")
                    .valueType(DataType.NUMBER)
                    .rule(rule)
                    .build();
            when(ruleConditionRepository.findById(conditionId))
                    .thenReturn(Optional.of(cond));
            when(ruleConditionRepository.save(any(RuleCondition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RuleConditionRequest request = new RuleConditionRequest();
            request.setField("age");
            request.setOperator(Operator.GREATER_OR_EQUAL);
            request.setValue("21");
            request.setValueType(DataType.NUMBER);

            RuleConditionResponse response =
                    ruleConditionService.update(ruleSetId, ruleId, conditionId, tenantId, request);

            assertThat(response.getOperator()).isEqualTo(Operator.GREATER_OR_EQUAL);
            assertThat(response.getValue()).isEqualTo("21");
            verify(ruleConditionRepository).save(cond);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("doit supprimer une condition")
        void shouldDeleteCondition() {
            Long conditionId = 4000L;
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleCondition cond = RuleCondition.builder()
                    .id(conditionId)
                    .field("age")
                    .operator(Operator.EQUALS)
                    .value("18")
                    .valueType(DataType.NUMBER)
                    .rule(rule)
                    .build();
            when(ruleConditionRepository.findById(conditionId))
                    .thenReturn(Optional.of(cond));

            ruleConditionService.delete(ruleSetId, ruleId, conditionId, tenantId);

            verify(ruleConditionRepository).delete(cond);
        }
    }
}

