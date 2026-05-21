package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.mappers.RuleSetMapper;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleSetServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RuleSetMapper ruleSetMapper;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock NotificationProducer notificationProducer;
    @Mock RuleSetDocumentationAgent ruleSetDocumentationAgent;
    @Mock N8nWebhookService n8nWebhookService;
    @Mock UserRepository userRepository;
    @Mock MailService mailService;

    @InjectMocks RuleSetServiceImpl ruleSetService;

    // ─── helpers ─────────────────────────────────────────────

    private Tenant tenant() {
        return Tenant.builder().id(10L).name("TenantA").build();
    }

    private RuleSet ruleSetWith(RuleSetStatus status, boolean hasRules) {
        List<Rule> rules = hasRules
                ? List.of(Rule.builder().id(1L).name("R1").build())
                : new ArrayList<>();
        return RuleSet.builder().id(1L).name("RS").status(status).rules(rules).tenant(tenant()).build();
    }

    private CreateRuleSetRequest createReq(String name) {
        CreateRuleSetRequest req = new CreateRuleSetRequest();
        req.setName(name);
        req.setEvaluationStrategy(EvaluationStrategy.FIRST_MATCH);
        return req;
    }

    private UpdateRuleSetRequest updateReq(String name) {
        UpdateRuleSetRequest req = new UpdateRuleSetRequest();
        req.setName(name);
        req.setEvaluationStrategy(EvaluationStrategy.ALL_MATCH);
        return req;
    }

    // ─── CREATE ──────────────────────────────────────────────

    @Test
    void create_whenTenantNotFound_throwsNotFound() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleSetService.create(createReq("RS"), 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_whenDuplicateName_throwsConflict() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant()));
        when(ruleSetRepository.existsByNameAndTenantId("Existing", 10L)).thenReturn(true);

        assertThatThrownBy(() -> ruleSetService.create(createReq("Existing"), 10L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Existing");
    }

    @Test
    void create_success_persistsAndPublishesAuditAndCallsN8n() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant()));
        when(ruleSetRepository.existsByNameAndTenantId("NewRS", 10L)).thenReturn(false);
        RuleSet persisted = ruleSetWith(RuleSetStatus.DRAFT, false);
        persisted.setId(5L);
        when(ruleSetRepository.save(any())).thenReturn(persisted);
        RuleSetResponse resp = RuleSetResponse.builder().id(5L).name("NewRS").build();
        when(ruleSetMapper.toDto(persisted)).thenReturn(resp);
        doNothing().when(n8nWebhookService).notifyRuleSetCreated(any(), any());

        RuleSetResponse result = ruleSetService.create(createReq("NewRS"), 10L);

        assertThat(result.getId()).isEqualTo(5L);
        verify(auditProducer).publish(eq(AuditAction.RULESET_CREATED), eq("RULESET"),
                eq(5L), isNull(), eq("NewRS"), eq(10L), any(), any());
        verify(n8nWebhookService).notifyRuleSetCreated(any(), any());
    }

    // ─── ACTIVATE ────────────────────────────────────────────

    @Test
    void activate_whenAlreadyActive_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.ACTIVE, true)));

        assertThatThrownBy(() -> ruleSetService.activate(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already active");
    }

    @Test
    void activate_whenNoRules_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.DRAFT, false)));

        assertThatThrownBy(() -> ruleSetService.activate(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no rules");
    }

    @Test
    void activate_success_setsStatusActiveAndTriggersDocumentation() {
        RuleSet rs = ruleSetWith(RuleSetStatus.DRAFT, true);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleSetRepository.save(any())).thenReturn(rs);
        when(ruleSetMapper.toDto(rs)).thenReturn(RuleSetResponse.builder().id(1L).build());
        when(userRepository.findAllByTenantId(10L)).thenReturn(List.of());
        doNothing().when(n8nWebhookService).notifyRuleSetActivated(any(), any());

        ruleSetService.activate(1L, 10L);

        assertThat(rs.getStatus()).isEqualTo(RuleSetStatus.ACTIVE);
        verify(ruleSetDocumentationAgent).generateDocumentation(1L, 10L);
        verify(notificationProducer).publish(any(), any(), any(), eq(10L), eq(1L), eq("RULESET"));
    }

    @Test
    void activate_whenNotFound_throwsNotFound() {
        when(ruleSetRepository.findByIdAndTenantId(77L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleSetService.activate(77L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── ARCHIVE ─────────────────────────────────────────────

    @Test
    void archive_whenAlreadyArchived_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.ARCHIVED, false)));

        assertThatThrownBy(() -> ruleSetService.archive(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already archived");
    }

    @Test
    void archive_success_setsStatusArchived() {
        RuleSet rs = ruleSetWith(RuleSetStatus.ACTIVE, true);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleSetRepository.save(any())).thenReturn(rs);
        when(ruleSetMapper.toDto(rs)).thenReturn(RuleSetResponse.builder().id(1L).build());

        ruleSetService.archive(1L, 10L);

        assertThat(rs.getStatus()).isEqualTo(RuleSetStatus.ARCHIVED);
        verify(auditProducer).publish(eq(AuditAction.RULESET_ARCHIVED), any(), any(), any(), any(), any(), any(), any());
    }

    // ─── UPDATE ──────────────────────────────────────────────

    @Test
    void update_whenArchivedRuleSet_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.ARCHIVED, false)));

        assertThatThrownBy(() -> ruleSetService.update(1L, 10L, updateReq("X")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void update_whenDuplicateNameForOther_throwsConflict() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.DRAFT, false)));
        when(ruleSetRepository.existsByNameAndTenantIdAndIdNot("TakenName", 10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> ruleSetService.update(1L, 10L, updateReq("TakenName")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("TakenName");
    }

    @Test
    void update_success_updatesNameAndPublishesAudit() {
        RuleSet rs = ruleSetWith(RuleSetStatus.DRAFT, false);
        rs.setName("OldName");
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleSetRepository.existsByNameAndTenantIdAndIdNot(any(), any(), any())).thenReturn(false);
        when(ruleSetRepository.save(any())).thenReturn(rs);
        when(ruleSetMapper.toDto(rs)).thenReturn(RuleSetResponse.builder().id(1L).name("NewName").build());

        ruleSetService.update(1L, 10L, updateReq("NewName"));

        assertThat(rs.getName()).isEqualTo("NewName");
        verify(auditProducer).publish(eq(AuditAction.RULESET_UPDATED), any(), any(),
                eq("OldName"), eq("NewName"), any(), any(), any());
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_whenActive_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.ACTIVE, true)));

        assertThatThrownBy(() -> ruleSetService.delete(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("active");
    }

    @Test
    void delete_whenDraft_deletesAndPublishesAudit() {
        RuleSet rs = ruleSetWith(RuleSetStatus.DRAFT, false);
        rs.setName("ToDelete");
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));

        ruleSetService.delete(1L, 10L);

        verify(ruleSetRepository).delete(rs);
        verify(auditProducer).publish(eq(AuditAction.RULESET_DELETED), any(), any(),
                eq("ToDelete"), isNull(), any(), any(), any());
    }

    @Test
    void delete_whenArchived_deletesSuccessfully() {
        RuleSet rs = ruleSetWith(RuleSetStatus.ARCHIVED, false);
        rs.setName("Archived");
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));

        ruleSetService.delete(1L, 10L);

        verify(ruleSetRepository).delete(rs);
    }

    // ─── MOVE TO DRAFT ───────────────────────────────────────

    @Test
    void moveToDraft_whenAlreadyDraft_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.DRAFT, false)));

        assertThatThrownBy(() -> ruleSetService.moveToDraft(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in draft");
    }

    @Test
    void moveToDraft_whenActive_changesStatusToDraft() {
        RuleSet rs = ruleSetWith(RuleSetStatus.ACTIVE, true);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleSetRepository.save(any())).thenReturn(rs);
        when(ruleSetMapper.toDto(rs)).thenReturn(RuleSetResponse.builder().id(1L).build());

        ruleSetService.moveToDraft(1L, 10L);

        assertThat(rs.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
    }

    // ─── UNARCHIVE ───────────────────────────────────────────

    @Test
    void unarchive_whenNotArchived_throwsBadRequest() {
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ruleSetWith(RuleSetStatus.ACTIVE, true)));

        assertThatThrownBy(() -> ruleSetService.unarchive(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void unarchive_success_setsStatusToDraft() {
        RuleSet rs = ruleSetWith(RuleSetStatus.ARCHIVED, false);
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(rs));
        when(ruleSetRepository.save(any())).thenReturn(rs);
        when(ruleSetMapper.toDto(rs)).thenReturn(RuleSetResponse.builder().id(1L).build());

        ruleSetService.unarchive(1L, 10L);

        assertThat(rs.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
        verify(auditProducer).publish(eq(AuditAction.RULESET_UNARCHIVED), any(), any(), any(), any(), any(), any(), any());
    }

    // ─── GET ALL – status filter ──────────────────────────────

    @Test
    void getAll_withInvalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> ruleSetService.getAll(10L, "", "UNKNOWN", 0, 10, "id", "asc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DRAFT, ACTIVE or ARCHIVED");
    }

    @Test
    void getAll_withValidStatusFilter_queriesRepository() {
        Page<RuleSet> page = new PageImpl<>(List.of());
        when(ruleSetRepository.findAllByTenantWithFilters(eq(10L), any(), eq(RuleSetStatus.ACTIVE), any()))
                .thenReturn(page);

        ruleSetService.getAll(10L, "", "ACTIVE", 0, 10, "id", "asc");

        verify(ruleSetRepository).findAllByTenantWithFilters(eq(10L), any(), eq(RuleSetStatus.ACTIVE), any());
    }

    @Test
    void getAll_withNullStatus_queriesWithNullStatus() {
        Page<RuleSet> page = new PageImpl<>(List.of());
        when(ruleSetRepository.findAllByTenantWithFilters(eq(10L), any(), isNull(), any()))
                .thenReturn(page);

        ruleSetService.getAll(10L, null, null, 0, 10, "id", "desc");

        verify(ruleSetRepository).findAllByTenantWithFilters(eq(10L), any(), isNull(), any());
    }
}