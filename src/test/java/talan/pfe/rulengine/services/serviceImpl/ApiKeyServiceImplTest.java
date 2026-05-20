package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import talan.pfe.rulengine.dtos.request.CreateApiKeyRequest;
import talan.pfe.rulengine.dtos.response.ApiKeyCreatedResponse;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;
import talan.pfe.rulengine.entites.ApiKey;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.mappers.ApiKeyMapper;
import talan.pfe.rulengine.repositories.ApiKeyRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyServiceImpl")
class ApiKeyServiceImplTest {

    @Mock ApiKeyRepository apiKeyRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock ApiKeyMapper apiKeyMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock NotificationProducer notificationProducer;

    @InjectMocks ApiKeyServiceImpl service;

    private Tenant tenant;
    private RuleSet ruleSet;
    private ApiKey apiKey;
    private ApiKeyResponse apiKeyResponse;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        ruleSet = RuleSet.builder().id(10L).name("Credit RS").status(RuleSetStatus.ACTIVE)
                .tenant(tenant).rules(new ArrayList<>()).build();

        apiKey = ApiKey.builder()
                .id(5L).name("MyKey").keyHash("hashed").keyPrefix("raas_ABCDE1")
                .tenant(tenant).ruleSet(ruleSet).active(true).build();

        apiKeyResponse = ApiKeyResponse.builder().id(5L).name("MyKey").active(true).build();
    }

    // ─── GENERATE ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("should generate API key and return raw key in response")
        void generate_success() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(ruleSet));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(apiKey);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            CreateApiKeyRequest req = new CreateApiKeyRequest();
            req.setName("MyKey");
            req.setRuleSetId(10L);

            ApiKeyCreatedResponse result = service.generate(req, 1L);

            assertThat(result.getName()).isEqualTo("MyKey");
            assertThat(result.getRawKey()).startsWith("raas_");
            assertThat(result.getRawKey().length()).isGreaterThan(12);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant not found")
        void generate_tenantNotFound() {
            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

            CreateApiKeyRequest req = new CreateApiKeyRequest();
            req.setRuleSetId(10L);

            assertThatThrownBy(() -> service.generate(req, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when RuleSet not found for tenant")
        void generate_ruleSetNotFound() {
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(ruleSetRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

            CreateApiKeyRequest req = new CreateApiKeyRequest();
            req.setRuleSetId(99L);

            assertThatThrownBy(() -> service.generate(req, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── GET ALL ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAll()")
    class GetAll {

        @Test
        @DisplayName("should return list of API keys for tenant")
        void getAll_returnsList() {
            when(apiKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(apiKey));
            when(apiKeyMapper.toDtoList(any())).thenReturn(List.of(apiKeyResponse));

            List<ApiKeyResponse> result = service.getAll(1L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("MyKey");
        }
    }

    // ─── REVOKE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revoke()")
    class Revoke {

        @Test
        @DisplayName("should revoke an active API key")
        void revoke_success() {
            when(apiKeyRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(apiKey));
            when(apiKeyRepository.save(any())).thenReturn(apiKey);
            when(apiKeyMapper.toDto(any())).thenReturn(apiKeyResponse);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(notificationProducer).publish(any(), any(), any(), any(), any(), any());

            service.revoke(5L, 1L);
            assertThat(apiKey.isActive()).isFalse();
        }

        @Test
        @DisplayName("should throw BadRequestException when key is already revoked")
        void revoke_alreadyRevoked_throwsBadRequest() {
            apiKey.setActive(false);
            when(apiKeyRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(apiKey));

            assertThatThrownBy(() -> service.revoke(5L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already revoked");
        }
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should delete a revoked API key")
        void delete_success() {
            apiKey.setActive(false);
            when(apiKeyRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(apiKey));
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());
            doNothing().when(apiKeyRepository).delete(apiKey);

            service.delete(5L, 1L);
            verify(apiKeyRepository).delete(apiKey);
        }

        @Test
        @DisplayName("should throw BadRequestException when deleting an active key")
        void delete_activeKey_throwsBadRequest() {
            when(apiKeyRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(apiKey));

            assertThatThrownBy(() -> service.delete(5L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Revoke it first");
        }
    }

    // ─── REGENERATE ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("regenerate()")
    class Regenerate {

        @Test
        @DisplayName("should deactivate old key and create new one")
        void regenerate_success() {
            when(apiKeyRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(apiKey));
            when(passwordEncoder.encode(anyString())).thenReturn("newHash");
            when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(apiKey);
            doNothing().when(auditProducer).publish(any(), any(), any(), any(), any(), any(), any(), any());

            ApiKeyCreatedResponse result = service.regenerate(5L, 1L);

            // old key should be deactivated (first save call)
            assertThat(result).isNotNull();
            assertThat(result.getRawKey()).startsWith("raas_");
            // save called at least twice: once to deactivate old, once to persist new
            verify(apiKeyRepository, atLeast(2)).save(any(ApiKey.class));
        }
    }
}
