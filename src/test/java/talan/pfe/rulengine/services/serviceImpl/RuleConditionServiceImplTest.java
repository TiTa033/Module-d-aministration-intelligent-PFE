package talan.pfe.rulengine.services.serviceImpl;

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
import talan.pfe.rulengine.enums.*;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.RuleConditionMapper;
import talan.pfe.rulengine.repositories.RuleConditionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleConditionServiceImpl")
class RuleConditionServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleRepository ruleRepository;
    @Mock RuleConditionRepository ruleConditionRepository;
    @Mock RuleConditionMapper ruleConditionMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks RuleConditionServiceImpl service;

    private RuleSet draftRuleSet;
    private RuleSet archivedRuleSet;
    private Rule rule;
    private RuleCondition condition;
    private RuleConditionResponse conditionResponse;

    @BeforeEach
    void setUp() {
        draftRuleSet = RuleSet.builder().id(10L).name("RS")
                .status(RuleSetStatus.DRAFT)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .rules(new ArrayList<>()).build();

        archivedRuleSet = RuleSet.builder().id(10L).name("RS")
                .status(RuleSetStatus.ARCHIVED)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .rules(new ArrayList<>()).build();

        rule = Rule.builder().id(1L).name("R1").priority(1)
                .logicOperator(LogicOperator.AND).enabled(true)
                .ruleSet(draftRuleSet)
                .conditions(new ArrayList<>()).actions(new ArrayList<>()).build();

        condition = RuleCondition.builder().id(3L)
                .field("amount").operator(Operator.GREATER_THAN)
                .value("1000").valueType(DataType.NUMBER)
                .rule(rule).build();

        conditionResponse = RuleConditionResponse.builder().id(3L)
                .field("amount").operator(Operator.GREATER_THAN)
                .value("1000").valueType(DataType.NUMBER).build();
    }

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("should create condition on DRAFT ruleset")
        void create_success() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleConditionRepository.save(any(RuleCondition.class))).thenReturn(condition);
            when(ruleConditionMapper.toDto(any())).thenReturn(conditionResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            RuleConditionRequest req = new RuleConditionRequest();
            req.setField("amount");
            req.setOperator(Operator.GREATER_THAN);
            req.setValue("1000");
            req.setValueType(DataType.NUMBER);

            RuleConditionResponse result = service.create(10L, 1L, 1L, req);
            assertThat(result.getField()).isEqualTo("amount");
            assertThat(result.getOperator()).isEqualTo(Operator.GREATER_THAN);
        }

        @Test @DisplayName("should throw BadRequestException on ARCHIVED ruleset")
        void create_archivedRuleSet_throwsBadRequest() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(archivedRuleSet));
            rule.setRuleSet(archivedRuleSet);
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));

            RuleConditionRequest req = new RuleConditionRequest();
            req.setField("amount");
            req.setOperator(Operator.GREATER_THAN);
            req.setValue("1000");
            req.setValueType(DataType.NUMBER);

            assertThatThrownBy(() -> service.create(10L, 1L, 1L, req))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("archived");
        }
    }

    @Nested @DisplayName("getAll()")
    class GetAll {

        @Test @DisplayName("should return all conditions for a rule")
        void getAll_returnsList() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleConditionRepository.findAllByRuleIdOrderByIdAsc(1L)).thenReturn(List.of(condition));
            when(ruleConditionMapper.toDtoList(any())).thenReturn(List.of(conditionResponse));

            List<RuleConditionResponse> result = service.getAll(10L, 1L, 1L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getField()).isEqualTo("amount");
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {

        @Test @DisplayName("should delete condition from DRAFT ruleset")
        void delete_success() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleConditionRepository.findById(3L)).thenReturn(Optional.of(condition));
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(ruleConditionRepository).delete(condition);

            service.delete(10L, 1L, 3L, 1L);
            verify(ruleConditionRepository).delete(condition);
        }

        @Test @DisplayName("should throw ResourceNotFoundException when condition not found")
        void delete_conditionNotFound() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleConditionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(10L, 1L, 99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
