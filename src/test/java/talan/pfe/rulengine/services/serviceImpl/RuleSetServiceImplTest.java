package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.request.UpdateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleSetServiceImpl")
class RuleSetServiceImplTest {

    @Mock RuleSetRepository ruleSetRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RuleSetMapper ruleSetMapper;
    @Mock AuditProducer auditProducer;
    @Mock NotificationProducer notificationProducer;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock RuleSetDocumentationAgent ruleSetDocumentationAgent;
    @Mock N8nWebhookService n8nWebhookService;
    @Mock UserRepository userRepository;
    @Mock MailService mailService;

    @InjectMocks RuleSetServiceImpl service;

    private Tenant tenant;
    private RuleSet ruleSet;
    private RuleSetResponse ruleSetResponse;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();

        ruleSet = RuleSet.builder()
                .id(10L)
                .name("Credit Scoring")
                .description("desc")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .status(RuleSetStatus.DRAFT)
                .tenant(tenant)
                .rules(new ArrayList<>())
                .build();

        ruleSetResponse = RuleSetResponse.builder()
                .id(10L)
                .name("Credit Scoring")
                .build();
    }

    // ─── CREATE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create RuleSet and return response when name is unique")
        void create_success() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.existsByNameAndTenantId("Credit Scoring", 1L)).thenReturn(false);
            when(ruleSetRepository.save(any(RuleSet.class))).thenReturn(ruleSet);
            when(ruleSetMapper.toDto(any(RuleSet.class))).thenReturn(ruleSetResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            CreateRuleSetRequest req = new CreateRuleSetRequest();
            req.setName("Credit Scoring");
            req.setDescription("desc");
            req.setEvaluationStrategy(EvaluationStrategy.FIRST_MATCH);

            RuleSetResponse result = service.create(req, 1L);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getName()).isEqualTo("Credit Scoring");
            verify(ruleSetRepository).save(any(RuleSet.class));
        }

        @Test
        @DisplayName("should throw ConflictException when name already exists in tenant")
        void create_duplicateName_throwsConflict() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.existsByNameAndTenantId("Credit Scoring", 1L)).thenReturn(true);

            CreateRuleSetRequest req = new CreateRuleSetRequest();
            req.setName("Credit Scoring");
            req.setEvaluationStrategy(EvaluationStrategy.FIRST_MATCH);

            assertThatThrownBy(() -> service.create(req, 1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Credit Scoring");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant does not exist")
        void create_tenantNotFound_throwsNotFound() {
            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

            CreateRuleSetRequest req = new CreateRuleSetRequest();
            req.setName("RS");
            req.setEvaluationStrategy(EvaluationStrategy.FIRST_MATCH);

            assertThatThrownBy(() -> service.create(req, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── GET BY ID ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return RuleSet when found for tenant")
        void getById_found() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(ruleSetMapper.toDto(ruleSet)).thenReturn(ruleSetResponse);

            RuleSetResponse result = service.getById(10L, 1L);
            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when RuleSet not found")
        void getById_notFound() {
            when(ruleSetRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(999L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── ACTIVATE ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("activate()")
    class Activate {

        @Test
        @DisplayName("should activate a DRAFT RuleSet that has rules")
        void activate_success() {
            // add a dummy rule so isEmpty() = false
            ruleSet.getRules().add(talan.pfe.rulengine.entites.Rule.builder()
                    .id(1L).name("R1").priority(1)
                    .logicOperator(talan.pfe.rulengine.enums.LogicOperator.AND)
                    .enabled(true).build());

            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(ruleSetRepository.save(any())).thenReturn(ruleSet);
            when(ruleSetMapper.toDto(any())).thenReturn(ruleSetResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());
            when(userRepository.findAllByTenantId(1L)).thenReturn(new ArrayList<>());
            doNothing().when(ruleSetDocumentationAgent).generateDocumentation(any(), any());
            doNothing().when(n8nWebhookService).notifyRuleSetActivated(any(), any());
            when(currentUserResolver.requireUser()).thenThrow(new RuntimeException("no user"));

            RuleSetResponse result = service.activate(10L, 1L);
            assertThat(result).isNotNull();
            assertThat(ruleSet.getStatus()).isEqualTo(RuleSetStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw BadRequestException when RuleSet is already ACTIVE")
        void activate_alreadyActive_throwsBadRequest() {
            ruleSet.setStatus(RuleSetStatus.ACTIVE);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> service.activate(10L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already active");
        }

        @Test
        @DisplayName("should throw BadRequestException when RuleSet has no rules")
        void activate_noRules_throwsBadRequest() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> service.activate(10L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("no rules");
        }
    }

    // ─── ARCHIVE ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("archive()")
    class Archive {

        @Test
        @DisplayName("should archive an ACTIVE RuleSet")
        void archive_success() {
            ruleSet.setStatus(RuleSetStatus.ACTIVE);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(ruleSetRepository.save(any())).thenReturn(ruleSet);
            when(ruleSetMapper.toDto(any())).thenReturn(ruleSetResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            service.archive(10L, 1L);
            assertThat(ruleSet.getStatus()).isEqualTo(RuleSetStatus.ARCHIVED);
        }

        @Test
        @DisplayName("should throw BadRequestException when already ARCHIVED")
        void archive_alreadyArchived_throwsBadRequest() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> service.archive(10L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already archived");
        }
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete a DRAFT RuleSet")
        void delete_success() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(ruleSetRepository).delete(ruleSet);

            service.delete(10L, 1L);
            verify(ruleSetRepository).delete(ruleSet);
        }

        @Test
        @DisplayName("should throw BadRequestException when deleting an ACTIVE RuleSet")
        void delete_activeRuleSet_throwsBadRequest() {
            ruleSet.setStatus(RuleSetStatus.ACTIVE);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> service.delete(10L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Archive it first");
        }
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should throw BadRequestException when updating an ARCHIVED RuleSet")
        void update_archived_throwsBadRequest() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(ruleSetRepository.existsByNameAndTenantIdAndIdNot("NewName", 1L, 10L)).thenReturn(false);

            UpdateRuleSetRequest req = new UpdateRuleSetRequest();
            req.setName("NewName");
            req.setEvaluationStrategy(EvaluationStrategy.ALL_MATCH);

            assertThatThrownBy(() -> service.update(10L, 1L, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("archived");
        }
    }

    // ─── UNARCHIVE ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unarchive()")
    class Unarchive {

        @Test
        @DisplayName("should move ARCHIVED RuleSet back to DRAFT")
        void unarchive_success() {
            ruleSet.setStatus(RuleSetStatus.ARCHIVED);
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(ruleSetRepository.save(any())).thenReturn(ruleSet);
            when(ruleSetMapper.toDto(any())).thenReturn(ruleSetResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            service.unarchive(10L, 1L);
            assertThat(ruleSet.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
        }

        @Test
        @DisplayName("should throw BadRequestException when unarchiving a non-ARCHIVED RuleSet")
        void unarchive_notArchived_throwsBadRequest() {
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));

            assertThatThrownBy(() -> service.unarchive(10L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Only archived");
        }
    }
}
