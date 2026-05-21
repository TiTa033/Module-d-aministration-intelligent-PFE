package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import talan.pfe.rulengine.dtos.request.CreateRuleRequest;
import talan.pfe.rulengine.dtos.request.RuleFilterRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleRequest;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.NotifType;
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
import talan.pfe.rulengine.services.RuleActionService;
import talan.pfe.rulengine.services.RuleConditionService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleServiceImplTest {

    @Mock RuleRepository ruleRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleMapper ruleMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock NotificationProducer notificationProducer;
    @Mock RuleConditionService ruleConditionService;
    @Mock RuleActionService ruleActionService;

    @InjectMocks RuleServiceImpl ruleService;

    // ─── helpers ─────────────────────────────────────────────

    private RuleSet ruleSet(RuleSetStatus status) {
        return RuleSet.builder().id(1L).status(status).build();
    }

    private Rule rule(Long id, String name, boolean enabled) {
        Rule r = Rule.builder().id(id).name(name).enabled(enabled).build();
        r.setPendingEnabled(null);
        return r;
    }

    private CreateRuleRequest createReq(String name, int priority) {
        CreateRuleRequest req = new CreateRuleRequest();
        req.setName(name);
        req.setPriority(priority);
        req.setLogicOperator(LogicOperator.AND);
        return req;
    }

    private UpdateRuleRequest updateReq(String name, int priority) {
        UpdateRuleRequest req = new UpdateRuleRequest();
        req.setName(name);
        req.setPriority(priority);
        req.setLogicOperator(LogicOperator.AND);
        return req;
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_whenRuleSetNotFound_throwsNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.create(99L, 10L, createReq("R", 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_whenArchivedRuleSet_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ARCHIVED)));

        assertThatThrownBy(() -> ruleService.create(1L, 10L, createReq("R", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void create_whenDuplicateName_throwsConflict() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.DRAFT)));
        when(ruleRepository.existsByNameAndRuleSetId("DupRule", 1L)).thenReturn(true);

        assertThatThrownBy(() -> ruleService.create(1L, 10L, createReq("DupRule", 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DupRule");
    }

    @Test
    void create_whenDuplicatePriority_throwsConflict() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.DRAFT)));
        when(ruleRepository.existsByNameAndRuleSetId("NewRule", 1L)).thenReturn(false);
        when(ruleRepository.existsByPriorityAndRuleSetId(3, 1L)).thenReturn(true);

        assertThatThrownBy(() -> ruleService.create(1L, 10L, createReq("NewRule", 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("priority 3");
    }

    @Test
    void create_success_savesRuleAndPublishesAudit() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.existsByNameAndRuleSetId(any(), any())).thenReturn(false);
        when(ruleRepository.existsByPriorityAndRuleSetId(any(), any())).thenReturn(false);
        Rule savedRule = rule(42L, "ValidRule", false);
        when(ruleRepository.save(any(Rule.class))).thenReturn(savedRule);
        RuleResponse expected = RuleResponse.builder().id(42L).name("ValidRule").build();
        when(ruleMapper.toDto(savedRule)).thenReturn(expected);

        RuleResponse result = ruleService.create(1L, 10L, createReq("ValidRule", 1));

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getName()).isEqualTo("ValidRule");
        verify(auditProducer).publish(eq(AuditAction.RULE_CREATED), eq("RULE"), eq(42L),
                isNull(), eq("ValidRule"), eq(10L), any(), any());
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_whenArchivedRuleSet_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ARCHIVED)));

        assertThatThrownBy(() -> ruleService.update(1L, 5L, 10L, updateReq("New", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void update_whenRuleNotFound_throwsNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.findByIdAndRuleSetId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.update(1L, 99L, 10L, updateReq("X", 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_whenDuplicateNameForOtherRule_throwsConflict() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L))
                .thenReturn(Optional.of(rule(5L, "OldName", true)));
        when(ruleRepository.existsByNameAndRuleSetIdAndIdNot("TakenName", 1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> ruleService.update(1L, 5L, 10L, updateReq("TakenName", 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("TakenName");
    }

    @Test
    void update_whenDuplicatePriorityForOtherRule_throwsConflict() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L))
                .thenReturn(Optional.of(rule(5L, "Rule5", true)));
        when(ruleRepository.existsByNameAndRuleSetIdAndIdNot("Rule5", 1L, 5L)).thenReturn(false);
        when(ruleRepository.existsByPriorityAndRuleSetIdAndIdNot(7, 1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> ruleService.update(1L, 5L, 10L, updateReq("Rule5", 7)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("priority 7");
    }

    @Test
    void update_success_updatesFieldsAndPublishesAudit() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule existingRule = rule(5L, "OldName", true);
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(existingRule));
        when(ruleRepository.existsByNameAndRuleSetIdAndIdNot(any(), any(), any())).thenReturn(false);
        when(ruleRepository.existsByPriorityAndRuleSetIdAndIdNot(any(), any(), any())).thenReturn(false);
        when(ruleRepository.save(any())).thenReturn(existingRule);
        when(ruleMapper.toDto(existingRule)).thenReturn(RuleResponse.builder().id(5L).name("NewName").build());

        RuleResponse result = ruleService.update(1L, 5L, 10L, updateReq("NewName", 2));

        assertThat(existingRule.getName()).isEqualTo("NewName");
        verify(auditProducer).publish(eq(AuditAction.RULE_UPDATED), eq("RULE"), eq(5L),
                eq("OldName"), eq("NewName"), eq(10L), any(), any());
    }

    // ─── ENABLE ──────────────────────────────────────────────

    @Test
    void enable_whenRuleAlreadyEnabled_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule r = rule(5L, "R", true); // enabled=true, pendingEnabled=null
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> ruleService.enable(1L, 5L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already enabled");
    }

    @Test
    void enable_whenRuleDisabled_setsPendingEnabledAndActivationDate() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule r = rule(5L, "MyRule", false);
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(r));
        when(ruleRepository.save(any())).thenReturn(r);
        when(ruleMapper.toDto(r)).thenReturn(RuleResponse.builder().id(5L).build());

        ruleService.enable(1L, 5L, 10L);

        assertThat(r.getPendingEnabled()).isTrue();
        assertThat(r.getActivationDate()).isNotNull();
        assertThat(r.getActivationDate().toLocalDate()).isEqualTo(LocalDate.now().plusDays(1));
    }

    @Test
    void enable_publishesInfoNotification() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule r = rule(5L, "TargetRule", false);
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(r));
        when(ruleRepository.save(any())).thenReturn(r);
        when(ruleMapper.toDto(r)).thenReturn(RuleResponse.builder().id(5L).build());

        ruleService.enable(1L, 5L, 10L);

        verify(notificationProducer).publish(
                contains("activée"), contains("TargetRule"),
                eq(NotifType.INFO), eq(10L), eq(5L), eq("RULE"));
    }

    // ─── DISABLE ─────────────────────────────────────────────

    @Test
    void disable_whenRuleAlreadyDisabled_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule r = rule(5L, "R", false);
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> ruleService.disable(1L, 5L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already disabled");
    }

    @Test
    void disable_whenRuleEnabled_setsPendingEnabledFalse() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule r = rule(5L, "R", true);
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(r));
        when(ruleRepository.save(any())).thenReturn(r);
        when(ruleMapper.toDto(r)).thenReturn(RuleResponse.builder().id(5L).build());

        ruleService.disable(1L, 5L, 10L);

        assertThat(r.getPendingEnabled()).isFalse();
        assertThat(r.getActivationDate()).isNotNull();
    }

    @Test
    void disable_publishesWarningNotification() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule r = rule(5L, "DisabledRule", true);
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(r));
        when(ruleRepository.save(any())).thenReturn(r);
        when(ruleMapper.toDto(r)).thenReturn(RuleResponse.builder().id(5L).build());

        ruleService.disable(1L, 5L, 10L);

        verify(notificationProducer).publish(
                any(), contains("DisabledRule"),
                eq(NotifType.WARNING), eq(10L), eq(5L), eq("RULE"));
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_whenArchivedRuleSet_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ARCHIVED)));

        assertThatThrownBy(() -> ruleService.delete(1L, 5L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void delete_whenRuleNotFound_throwsNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.findByIdAndRuleSetId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.delete(1L, 99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_success_publishesAuditAndDeletesRule() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Rule r = rule(5L, "DeleteMe", true);
        when(ruleRepository.findByIdAndRuleSetId(5L, 1L)).thenReturn(Optional.of(r));

        ruleService.delete(1L, 5L, 10L);

        verify(auditProducer).publish(eq(AuditAction.RULE_DELETED), eq("RULE"), eq(5L),
                eq("DeleteMe"), isNull(), eq(10L), any(), any());
        verify(ruleRepository).delete(r);
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_withNullEnabledFilter_passesNullToRepository() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.findAllByRuleSetWithFilters(eq(1L), any(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        RuleFilterRequest filter = new RuleFilterRequest();
        filter.setSearch(""); filter.setEnabled(null);
        filter.setPage(0); filter.setSize(10);
        filter.setSortBy("priority"); filter.setSortDir("asc");

        ruleService.getAll(1L, 10L, filter);

        verify(ruleRepository).findAllByRuleSetWithFilters(eq(1L), any(), isNull(), any());
    }

    @Test
    void getAll_withEnabledTrueFilter_parsesBooleanAndFilters() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        Page<Rule> page = new PageImpl<>(List.of());
        when(ruleRepository.findAllByRuleSetWithFilters(eq(1L), any(), eq(true), any()))
                .thenReturn(page);

        RuleFilterRequest filter = new RuleFilterRequest();
        filter.setSearch(""); filter.setEnabled("true");
        filter.setPage(0); filter.setSize(5);
        filter.setSortBy("name"); filter.setSortDir("desc");

        ruleService.getAll(1L, 10L, filter);

        verify(ruleRepository).findAllByRuleSetWithFilters(eq(1L), any(), eq(true), any());
    }

    @Test
    void getById_whenNotFound_throwsNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.findByIdAndRuleSetId(77L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.getById(1L, 77L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("77");
    }

    @Test
    void getAllList_delegatesToRepositoryOrderedByPriority() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSet(RuleSetStatus.ACTIVE)));
        when(ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(1L)).thenReturn(List.of());
        when(ruleMapper.toDtoList(any())).thenReturn(List.of());

        ruleService.getAllList(1L, 10L);

        verify(ruleRepository).findAllByRuleSetIdOrderByPriorityAsc(1L);
    }
}