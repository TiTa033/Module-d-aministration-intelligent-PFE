package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.RuleActionMapper;
import talan.pfe.rulengine.repositories.RuleActionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleActionServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleRepository ruleRepository;
    @Mock RuleActionRepository ruleActionRepository;
    @Mock RuleActionMapper ruleActionMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks RuleActionServiceImpl service;

    private RuleSet activeRuleSet(Long id, Long tenantId) {
        return RuleSet.builder().id(id)
                .status(RuleSetStatus.ACTIVE)
                .tenant(Tenant.builder().id(tenantId).build())
                .build();
    }

    private RuleSet archivedRuleSet(Long id, Long tenantId) {
        return RuleSet.builder().id(id)
                .status(RuleSetStatus.ARCHIVED)
                .tenant(Tenant.builder().id(tenantId).build())
                .build();
    }

    private Rule rule(Long id, RuleSet rs) {
        return Rule.builder().id(id).ruleSet(rs).build();
    }

    private RuleActionRequest request() {
        RuleActionRequest r = new RuleActionRequest();
        r.setActionType(ActionType.SET_VALUE);
        r.setOutputKey("result");
        r.setOutputValue("approved");
        return r;
    }

    private RuleActionResponse response(Long id) {
        return RuleActionResponse.builder().id(id)
                .actionType(ActionType.SET_VALUE)
                .outputKey("result").outputValue("approved").build();
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_savesActionAndPublishesAudit() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleAction action = RuleAction.builder().id(99L).rule(r)
                .actionType(ActionType.SET_VALUE).outputKey("result").outputValue("approved").build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleActionRepository.save(any())).thenReturn(action);
        when(ruleActionMapper.toDto(action)).thenReturn(response(99L));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        RuleActionResponse result = service.create(1L, 2L, 10L, request());

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(r.getPendingUpdate()).isTrue();
        verify(ruleActionRepository).save(any());
        verify(auditProducer).publish(eq(AuditAction.ACTION_CREATED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_throwsWhenRuleSetArchived() {
        RuleSet rs = archivedRuleSet(1L, 10L);
        Rule r = rule(2L, rs);

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.create(1L, 2L, 10L, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void create_throwsWhenRuleSetNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, 2L, 10L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RuleSet not found");
    }

    @Test
    void create_throwsWhenRuleNotFound() {
        RuleSet rs = activeRuleSet(1L, 10L);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, 99L, 10L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Rule not found");
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returnsListForValidRule() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleAction a = RuleAction.builder().id(5L).rule(r)
                .actionType(ActionType.SET_VALUE).outputKey("k").outputValue("v").build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleActionRepository.findAllByRuleIdOrderByIdAsc(2L)).thenReturn(List.of(a));
        when(ruleActionMapper.toDtoList(List.of(a))).thenReturn(List.of(response(5L)));

        List<RuleActionResponse> result = service.getAll(1L, 2L, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(5L);
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returnsActionWhenFound() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleAction a = RuleAction.builder().id(5L).rule(r)
                .actionType(ActionType.SET_VALUE).outputKey("k").outputValue("v").build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleActionRepository.findById(5L)).thenReturn(Optional.of(a));
        when(ruleActionMapper.toDto(a)).thenReturn(response(5L));

        RuleActionResponse result = service.getById(1L, 2L, 5L, 10L);
        assertThat(result.getId()).isEqualTo(5L);
    }

    @Test
    void getById_throwsWhenActionBelongsToDifferentRule() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        Rule otherRule = rule(99L, rs);
        RuleAction a = RuleAction.builder().id(5L).rule(otherRule).build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleActionRepository.findById(5L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.getById(1L, 2L, 5L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RuleAction not found");
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_modifiesActionAndPublishesAudit() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleAction a = RuleAction.builder().id(5L).rule(r)
                .actionType(ActionType.SET_VALUE).outputKey("old_k").outputValue("old_v").build();

        RuleActionRequest req = request();
        req.setOutputKey("new_k");
        req.setOutputValue("new_v");

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleActionRepository.findById(5L)).thenReturn(Optional.of(a));
        when(ruleActionRepository.save(a)).thenReturn(a);
        when(ruleActionMapper.toDto(a)).thenReturn(response(5L));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.update(1L, 2L, 5L, 10L, req);

        assertThat(a.getOutputKey()).isEqualTo("new_k");
        assertThat(a.getOutputValue()).isEqualTo("new_v");
        verify(auditProducer).publish(eq(AuditAction.ACTION_UPDATED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_throwsWhenRuleSetArchived() {
        RuleSet rs = archivedRuleSet(1L, 10L);
        Rule r = rule(2L, rs);

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.update(1L, 2L, 5L, 10L, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_removesActionAndPublishesAudit() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleAction a = RuleAction.builder().id(5L).rule(r)
                .actionType(ActionType.SET_VALUE).outputKey("k").outputValue("v").build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleActionRepository.findById(5L)).thenReturn(Optional.of(a));
        doNothing().when(ruleActionRepository).delete(a);
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.delete(1L, 2L, 5L, 10L);

        verify(ruleActionRepository).delete(a);
        verify(auditProducer).publish(eq(AuditAction.ACTION_DELETED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void delete_throwsWhenRuleSetArchived() {
        RuleSet rs = archivedRuleSet(1L, 10L);
        Rule r = rule(2L, rs);

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.delete(1L, 2L, 5L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }
}