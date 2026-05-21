package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.RuleConditionMapper;
import talan.pfe.rulengine.repositories.RuleConditionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleConditionServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock RuleRepository ruleRepository;
    @Mock RuleConditionRepository ruleConditionRepository;
    @Mock RuleConditionMapper ruleConditionMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;

    @InjectMocks RuleConditionServiceImpl service;

    private RuleSet activeRuleSet(Long id, Long tenantId) {
        return RuleSet.builder().id(id).status(RuleSetStatus.ACTIVE)
                .tenant(Tenant.builder().id(tenantId).build()).build();
    }

    private RuleSet archivedRuleSet(Long id, Long tenantId) {
        return RuleSet.builder().id(id).status(RuleSetStatus.ARCHIVED)
                .tenant(Tenant.builder().id(tenantId).build()).build();
    }

    private Rule rule(Long id, RuleSet rs) {
        return Rule.builder().id(id).ruleSet(rs).build();
    }

    private RuleConditionRequest conditionRequest() {
        RuleConditionRequest r = new RuleConditionRequest();
        r.setField("income");
        r.setOperator(Operator.GREATER_THAN);
        r.setValue("50000");
        r.setValueType(DataType.NUMBER);
        return r;
    }

    private RuleConditionResponse conditionResponse(Long id) {
        return RuleConditionResponse.builder().id(id)
                .field("income").operator(Operator.GREATER_THAN)
                .value("50000").valueType(DataType.NUMBER).build();
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_savesConditionAndPublishesAudit() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleCondition cond = RuleCondition.builder().id(7L).rule(r)
                .field("income").operator(Operator.GREATER_THAN).value("50000").build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleConditionRepository.save(any())).thenReturn(cond);
        when(ruleConditionMapper.toDto(cond)).thenReturn(conditionResponse(7L));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        RuleConditionResponse result = service.create(1L, 2L, 10L, conditionRequest());

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(r.getPendingUpdate()).isTrue();
        verify(ruleConditionRepository).save(any());
        verify(auditProducer).publish(eq(AuditAction.CONDITION_CREATED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_throwsWhenRuleSetArchived() {
        RuleSet rs = archivedRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.create(1L, 2L, 10L, conditionRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void create_throwsWhenRuleSetNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, 2L, 10L, conditionRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RuleSet not found");
    }

    @Test
    void create_throwsWhenRuleNotFound() {
        RuleSet rs = activeRuleSet(1L, 10L);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, 99L, 10L, conditionRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Rule not found");
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    void getAll_returnsListForValidRule() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleCondition cond = RuleCondition.builder().id(7L).rule(r).build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleConditionRepository.findAllByRuleIdOrderByIdAsc(2L)).thenReturn(List.of(cond));
        when(ruleConditionMapper.toDtoList(List.of(cond))).thenReturn(List.of(conditionResponse(7L)));

        List<RuleConditionResponse> result = service.getAll(1L, 2L, 10L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(7L);
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returnsConditionWhenFound() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleCondition cond = RuleCondition.builder().id(7L).rule(r).build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleConditionRepository.findById(7L)).thenReturn(Optional.of(cond));
        when(ruleConditionMapper.toDto(cond)).thenReturn(conditionResponse(7L));

        RuleConditionResponse result = service.getById(1L, 2L, 7L, 10L);
        assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    void getById_throwsWhenConditionBelongsToDifferentRule() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        Rule otherRule = rule(99L, rs);
        RuleCondition cond = RuleCondition.builder().id(7L).rule(otherRule).build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleConditionRepository.findById(7L)).thenReturn(Optional.of(cond));

        assertThatThrownBy(() -> service.getById(1L, 2L, 7L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RuleCondition not found");
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_modifiesConditionAndPublishesAudit() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleCondition cond = RuleCondition.builder().id(7L).rule(r)
                .field("income").operator(Operator.GREATER_THAN).value("10000").build();

        RuleConditionRequest req = conditionRequest();
        req.setField("age");
        req.setValue("25");

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleConditionRepository.findById(7L)).thenReturn(Optional.of(cond));
        when(ruleConditionRepository.save(cond)).thenReturn(cond);
        when(ruleConditionMapper.toDto(cond)).thenReturn(conditionResponse(7L));
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.update(1L, 2L, 7L, 10L, req);

        assertThat(cond.getField()).isEqualTo("age");
        assertThat(cond.getValue()).isEqualTo("25");
        verify(auditProducer).publish(eq(AuditAction.CONDITION_UPDATED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_throwsWhenRuleSetArchived() {
        RuleSet rs = archivedRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.update(1L, 2L, 7L, 10L, conditionRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_removesConditionAndPublishesAudit() {
        RuleSet rs = activeRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        RuleCondition cond = RuleCondition.builder().id(7L).rule(r)
                .field("income").operator(Operator.GREATER_THAN).value("10000").build();

        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));
        when(ruleConditionRepository.findById(7L)).thenReturn(Optional.of(cond));
        doNothing().when(ruleConditionRepository).delete(cond);
        doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

        service.delete(1L, 2L, 7L, 10L);

        verify(ruleConditionRepository).delete(cond);
        verify(auditProducer).publish(eq(AuditAction.CONDITION_DELETED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void delete_throwsWhenRuleSetArchived() {
        RuleSet rs = archivedRuleSet(1L, 10L);
        Rule r = rule(2L, rs);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(2L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.delete(1L, 2L, 7L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }
}