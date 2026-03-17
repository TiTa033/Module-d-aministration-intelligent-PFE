package talan.pfe.rulengine.services;

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
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.RuleActionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleActionServiceTest {

    @Mock
    private RuleSetRepository ruleSetRepository;

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private RuleActionRepository ruleActionRepository;

    @InjectMocks
    private RuleActionService ruleActionService;

    private UUID tenantId;
    private UUID ruleSetId;
    private UUID ruleId;
    private RuleSet ruleSet;
    private Rule rule;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        ruleSetId = UUID.randomUUID();
        ruleId = UUID.randomUUID();

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
                .name("Decision rule")
                .priority(1)
                .enabled(true)
                .logicOperator(LogicOperator.AND)
                .ruleSet(ruleSet)
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("doit créer une RuleAction avec succès")
        void shouldCreateActionSuccessfully() {
            RuleActionRequest request = new RuleActionRequest();
            request.setActionType(ActionType.APPROVE);
            request.setOutputKey("decision");
            request.setOutputValue("APPROVED");

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleAction saved = RuleAction.builder()
                    .id(UUID.randomUUID())
                    .actionType(request.getActionType())
                    .outputKey(request.getOutputKey())
                    .outputValue(request.getOutputValue())
                    .rule(rule)
                    .build();
            when(ruleActionRepository.save(any(RuleAction.class)))
                    .thenReturn(saved);

            RuleActionResponse response =
                    ruleActionService.create(ruleSetId, ruleId, tenantId, request);

            assertThat(response).isNotNull();
            assertThat(response.getActionType()).isEqualTo(ActionType.APPROVE);
            assertThat(response.getOutputKey()).isEqualTo("decision");
            verify(ruleActionRepository).save(any(RuleAction.class));
        }

        @Test
        @DisplayName("doit refuser la création si le RuleSet est archivé")
        void shouldFailWhenRuleSetArchived() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleActionRequest request = new RuleActionRequest();
            request.setActionType(ActionType.APPROVE);
            request.setOutputKey("decision");
            request.setOutputValue("APPROVED");

            assertThatThrownBy(() ->
                    ruleActionService.create(ruleSetId, ruleId, tenantId, request))
                    .isInstanceOf(BadRequestException.class);
            verify(ruleActionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("doit retourner toutes les actions de la règle")
        void shouldReturnAllActions() {
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleAction action = RuleAction.builder()
                    .id(UUID.randomUUID())
                    .actionType(ActionType.APPROVE)
                    .outputKey("decision")
                    .outputValue("APPROVED")
                    .rule(rule)
                    .build();
            when(ruleActionRepository.findAllByRuleIdOrderByIdAsc(ruleId))
                    .thenReturn(List.of(action));

            List<RuleActionResponse> result =
                    ruleActionService.getAll(ruleSetId, ruleId, tenantId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOutputKey()).isEqualTo("decision");
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("doit retourner une action par id")
        void shouldReturnActionById() {
            UUID actionId = UUID.randomUUID();
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleAction action = RuleAction.builder()
                    .id(actionId)
                    .actionType(ActionType.APPROVE)
                    .outputKey("decision")
                    .outputValue("APPROVED")
                    .rule(rule)
                    .build();
            when(ruleActionRepository.findById(actionId))
                    .thenReturn(Optional.of(action));

            RuleActionResponse response =
                    ruleActionService.getById(ruleSetId, ruleId, actionId, tenantId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(actionId);
            assertThat(response.getActionType()).isEqualTo(ActionType.APPROVE);
        }

        @Test
        @DisplayName("doit lever une exception si l'action n'appartient pas à la règle")
        void shouldThrowWhenActionNotInRule() {
            UUID actionId = UUID.randomUUID();
            UUID anotherRuleId = UUID.randomUUID();

            Rule anotherRule = Rule.builder()
                    .id(anotherRuleId)
                    .ruleSet(ruleSet)
                    .build();

            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleAction action = RuleAction.builder()
                    .id(actionId)
                    .actionType(ActionType.APPROVE)
                    .outputKey("decision")
                    .outputValue("APPROVED")
                    .rule(anotherRule)
                    .build();
            when(ruleActionRepository.findById(actionId))
                    .thenReturn(Optional.of(action));

            assertThatThrownBy(() ->
                    ruleActionService.getById(ruleSetId, ruleId, actionId, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("doit mettre à jour une action")
        void shouldUpdateAction() {
            UUID actionId = UUID.randomUUID();
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleAction action = RuleAction.builder()
                    .id(actionId)
                    .actionType(ActionType.APPROVE)
                    .outputKey("decision")
                    .outputValue("APPROVED")
                    .rule(rule)
                    .build();
            when(ruleActionRepository.findById(actionId))
                    .thenReturn(Optional.of(action));
            when(ruleActionRepository.save(any(RuleAction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RuleActionRequest request = new RuleActionRequest();
            request.setActionType(ActionType.REJECT);
            request.setOutputKey("decision");
            request.setOutputValue("REJECTED");

            RuleActionResponse response =
                    ruleActionService.update(ruleSetId, ruleId, actionId, tenantId, request);

            assertThat(response.getActionType()).isEqualTo(ActionType.REJECT);
            assertThat(response.getOutputValue()).isEqualTo("REJECTED");
            verify(ruleActionRepository).save(action);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("doit supprimer une action")
        void shouldDeleteAction() {
            UUID actionId = UUID.randomUUID();
            when(ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId))
                    .thenReturn(Optional.of(ruleSet));
            when(ruleRepository.findByIdAndRuleSetId(ruleId, ruleSetId))
                    .thenReturn(Optional.of(rule));

            RuleAction action = RuleAction.builder()
                    .id(actionId)
                    .actionType(ActionType.APPROVE)
                    .outputKey("decision")
                    .outputValue("APPROVED")
                    .rule(rule)
                    .build();
            when(ruleActionRepository.findById(actionId))
                    .thenReturn(Optional.of(action));

            ruleActionService.delete(ruleSetId, ruleId, actionId, tenantId);

            verify(ruleActionRepository).delete(action);
        }
    }
}

