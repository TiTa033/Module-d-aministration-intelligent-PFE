package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateRuleRequest;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.mappers.RuleMapper;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleServiceImpl")
class RuleServiceImplTest {

    @Mock RuleRepository ruleRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleMapper ruleMapper;
    @Mock AuditProducer auditProducer;
    @Mock NotificationProducer notificationProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks RuleServiceImpl service;


    private RuleSet draftRuleSet;
    private RuleSet archivedRuleSet;
    private Rule rule;
    private RuleResponse ruleResponse;

    @BeforeEach
    void setUp() {
        draftRuleSet = RuleSet.builder()
                .id(10L).name("RS").status(RuleSetStatus.DRAFT)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .rules(new ArrayList<>()).build();


        archivedRuleSet = RuleSet.builder()
                .id(10L).name("RS").status(RuleSetStatus.ARCHIVED)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .rules(new ArrayList<>()).build();

        rule = Rule.builder()
                .id(1L).name("High Income").priority(1)
                .logicOperator(LogicOperator.AND)
                .score(10).enabled(true)
                .ruleSet(draftRuleSet)
                .conditions(new ArrayList<>()).actions(new ArrayList<>()).build();

        ruleResponse = RuleResponse.builder().id(1L).name("High Income").build();
    }

    // ─── CREATE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create rule when name and priority are unique")
        void create_success() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.existsByNameAndRuleSetId("High Income", 10L)).thenReturn(false);
            when(ruleRepository.existsByPriorityAndRuleSetId(1, 10L)).thenReturn(false);
            when(ruleRepository.save(any(Rule.class))).thenReturn(rule);
            when(ruleMapper.toDto(any(Rule.class))).thenReturn(ruleResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            CreateRuleRequest req = new CreateRuleRequest();
            req.setName("High Income");
            req.setPriority(1);
            req.setLogicOperator(LogicOperator.AND);
            req.setScore(10);

            RuleResponse result = service.create(10L, 1L, req);
            assertThat(result.getName()).isEqualTo("High Income");
        }

        @Test
        @DisplayName("should throw ConflictException on duplicate rule name")
        void create_duplicateName_throwsConflict() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.existsByNameAndRuleSetId("High Income", 10L)).thenReturn(true);

            CreateRuleRequest req = new CreateRuleRequest();
            req.setName("High Income");
            req.setPriority(1);

            assertThatThrownBy(() -> service.create(10L, 1L, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("High Income");
        }

        @Test
        @DisplayName("should throw ConflictException on duplicate priority")
        void create_duplicatePriority_throwsConflict() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.existsByNameAndRuleSetId("New Rule", 10L)).thenReturn(false);
            when(ruleRepository.existsByPriorityAndRuleSetId(1, 10L)).thenReturn(true);

            CreateRuleRequest req = new CreateRuleRequest();
            req.setName("New Rule");
            req.setPriority(1);

            assertThatThrownBy(() -> service.create(10L, 1L, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("priority");
        }

        @Test
        @DisplayName("should throw BadRequestException when RuleSet is ARCHIVED")
        void create_archivedRuleSet_throwsBadRequest() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(archivedRuleSet));

            CreateRuleRequest req = new CreateRuleRequest();
            req.setName("Rule");
            req.setPriority(1);

            assertThatThrownBy(() -> service.create(10L, 1L, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archived");
        }
    }

    // ─── GET BY ID ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return rule when found")
        void getById_found() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleMapper.toDto(rule)).thenReturn(ruleResponse);

            RuleResponse result = service.getById(10L, 1L, 1L);
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when rule does not exist")
        void getById_notFound() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(999L, 10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(10L, 999L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── ENABLE / DISABLE ────────────────────────────────────────────────────

    @Nested
    @DisplayName("enable() and disable()")
    class EnableDisable {

        @Test
        @DisplayName("enable() should set pendingEnabled=true")
        void enable_success() {
            rule.setEnabled(false);
            rule.setPendingEnabled(null);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any())).thenReturn(rule);
            when(ruleMapper.toDto(any())).thenReturn(ruleResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            service.enable(10L, 1L, 1L);
            assertThat(rule.getPendingEnabled()).isTrue();
        }

        @Test
        @DisplayName("enable() should throw BadRequestException when already enabled with no pending change")
        void enable_alreadyEnabled_throwsBadRequest() {
            rule.setEnabled(true);
            rule.setPendingEnabled(null);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> service.enable(10L, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already enabled");
        }

        @Test
        @DisplayName("disable() should set pendingEnabled=false")
        void disable_success() {
            rule.setEnabled(true);
            rule.setPendingEnabled(null);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any())).thenReturn(rule);
            when(ruleMapper.toDto(any())).thenReturn(ruleResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            service.disable(10L, 1L, 1L);
            assertThat(rule.getPendingEnabled()).isFalse();
        }

        @Test
        @DisplayName("disable() should throw BadRequestException when already disabled")
        void disable_alreadyDisabled_throwsBadRequest() {
            rule.setEnabled(false);
            rule.setPendingEnabled(null);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> service.disable(10L, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already disabled");
        }
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete rule from DRAFT RuleSet")
        void delete_success() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(draftRuleSet));
            when(ruleRepository.findByIdAndRuleSetId(1L, 10L)).thenReturn(Optional.of(rule));
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(ruleRepository).delete(rule);

            service.delete(10L, 1L, 1L);
            verify(ruleRepository).delete(rule);
        }

        @Test
        @DisplayName("should throw BadRequestException when RuleSet is ARCHIVED")
        void delete_archivedRuleSet_throwsBadRequest() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(archivedRuleSet));

            assertThatThrownBy(() -> service.delete(10L, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archived");
        }
    }
}
