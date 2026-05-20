package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleAction;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.RuleActionMapper;
import talan.pfe.rulengine.repositories.RuleActionRepository;
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
@DisplayName("RuleActionServiceImpl")
class RuleActionServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleRepository ruleRepository;
    @Mock RuleActionRepository ruleActionRepository;
    @Mock RuleActionMapper ruleActionMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks RuleActionServiceImpl service;

    private RuleSet draftRuleSet;
    private RuleSet archivedRuleSet;
    private Rule rule;
    private RuleAction action;
    private RuleActionResponse actionResponse;

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

        action = RuleAction.builder().id(5L)
                .actionType(ActionType.SET_VALUE)
                .outputKey("decision").outputValue("APPROVED")
                .rule(rule).build();

        actionResponse = RuleActionResponse.builder().id(5L)
                .actionType(ActionType.SET_VALUE)
                .outputKey("decision").outputValue("APPROVED").build();
    }

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("should create action on DRAFT ruleset")
        void create_success() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleActionRepository.save(any(RuleAction.class))).thenReturn(action);
            when(ruleActionMapper.toDto(any())).thenReturn(actionResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            RuleActionRequest req = new RuleActionRequest();
            req.setActionType(ActionType.SET_VALUE);
            req.setOutputKey("decision");
            req.setOutputValue("APPROVED");

            RuleActionResponse result = service.create(10L, 1L, 1L, req);
            assertThat(result.getOutputKey()).isEqualTo("decision");
        }

        @Test @DisplayName("should throw BadRequestException when ruleset is ARCHIVED")
        void create_archivedRuleSet_throwsBadRequest() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(archivedRuleSet));
            rule.setRuleSet(archivedRuleSet);
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));

            RuleActionRequest req = new RuleActionRequest();
            req.setActionType(ActionType.APPROVE);
            req.setOutputKey("decision");
            req.setOutputValue("APPROVED");

            assertThatThrownBy(() -> service.create(10L, 1L, 1L, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archived");
        }
    }

    @Nested @DisplayName("getAll()")
    class GetAll {

        @Test @DisplayName("should return all actions for a rule")
        void getAll_returnsList() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleActionRepository.findAllByRuleIdOrderByIdAsc(1L)).thenReturn(List.of(action));
            when(ruleActionMapper.toDtoList(any())).thenReturn(List.of(actionResponse));

            List<RuleActionResponse> result = service.getAll(10L, 1L, 1L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getActionType()).isEqualTo(ActionType.SET_VALUE);
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {

        @Test @DisplayName("should delete action from DRAFT ruleset")
        void delete_success() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleActionRepository.findById(5L)).thenReturn(Optional.of(action));
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(ruleActionRepository).delete(action);

            service.delete(10L, 1L, 5L, 1L);
            verify(ruleActionRepository).delete(action);
        }

        @Test @DisplayName("should throw ResourceNotFoundException when action not found")
        void delete_actionNotFound() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleActionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(10L, 1L, 99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("should throw BadRequestException when ruleset is ARCHIVED")
        void delete_archivedRuleSet_throwsBadRequest() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(archivedRuleSet));
            rule.setRuleSet(archivedRuleSet);
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> service.delete(10L, 1L, 5L, 1L))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("archived");
        }
    }
}
