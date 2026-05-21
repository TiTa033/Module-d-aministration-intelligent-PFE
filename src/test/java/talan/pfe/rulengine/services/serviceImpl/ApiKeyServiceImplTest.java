package talan.pfe.rulengine.services.serviceImpl;

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
import talan.pfe.rulengine.enums.AuditAction;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceImplTest {

    @Mock ApiKeyRepository apiKeyRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock ApiKeyMapper apiKeyMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditProducer auditProducer;
    @Mock CurrentUserResolver currentUserResolver;
    @Mock NotificationProducer notificationProducer;

    @InjectMocks ApiKeyServiceImpl apiKeyService;

    // ─── helpers ─────────────────────────────────────────────

    private Tenant tenant(Long id) {
        return Tenant.builder().id(id).name("T").slug("t").build();
    }

    private RuleSet ruleSet(Long id, Long tenantId) {
        return RuleSet.builder().id(id).name("RS").status(RuleSetStatus.ACTIVE)
                .tenant(tenant(tenantId)).build();
    }

    private ApiKey activeKey(Long id, String name) {
        return ApiKey.builder().id(id).name(name).active(true)
                .keyHash("hash").keyPrefix("raas_ABCDE12")
                .tenant(tenant(10L)).ruleSet(ruleSet(1L, 10L))
                .build();
    }

    private ApiKey revokedKey(Long id, String name) {
        return ApiKey.builder().id(id).name(name).active(false)
                .keyHash("hash").keyPrefix("raas_ABCDE12")
                .tenant(tenant(10L)).ruleSet(ruleSet(1L, 10L))
                .build();
    }

    private CreateApiKeyRequest createReq(String name, Long ruleSetId) {
        CreateApiKeyRequest req = new CreateApiKeyRequest();
        req.setName(name); req.setRuleSetId(ruleSetId);
        return req;
    }

    // ─── GENERATE ────────────────────────────────────────────

    @Test
    void generate_whenTenantNotFound_throwsNotFound() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.generate(createReq("K", 1L), 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");
    }

    @Test
    void generate_whenRuleSetNotFound_throwsNotFound() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant(10L)));
        when(ruleSetRepository.findByIdAndTenantId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.generate(createReq("K", 99L), 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RuleSet");
    }

    @Test
    void generate_rawKeyStartsWithRaasPrefix() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant(10L)));
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(ruleSet(1L, 10L)));
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        ApiKey savedKey = activeKey(42L, "MyKey");
        when(apiKeyRepository.save(any())).thenReturn(savedKey);

        ApiKeyCreatedResponse result = apiKeyService.generate(createReq("MyKey", 1L), 10L);

        assertThat(result.getRawKey()).startsWith("raas_");
    }

    @Test
    void generate_rawKeyHasMinimumLength() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant(10L)));
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(ruleSet(1L, 10L)));
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        ApiKey savedKey = activeKey(42L, "K");
        when(apiKeyRepository.save(any())).thenReturn(savedKey);

        ApiKeyCreatedResponse result = apiKeyService.generate(createReq("K", 1L), 10L);

        // raas_ prefix (5) + 32 base64-urlencoded bytes (≥ 43 chars)
        assertThat(result.getRawKey().length()).isGreaterThan(12);
    }

    @Test
    void generate_storesHashedKeyNotRawKey() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant(10L)));
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(ruleSet(1L, 10L)));
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash-value");
        ApiKey savedKey = activeKey(42L, "K");
        when(apiKeyRepository.save(any())).thenReturn(savedKey);

        apiKeyService.generate(createReq("K", 1L), 10L);

        // passwordEncoder must have been called to hash the raw key
        verify(passwordEncoder).encode(argThat(raw -> raw.toString().startsWith("raas_")));

        // the saved entity must use the hashed value, not the raw key
        verify(apiKeyRepository).save(argThat(k -> "bcrypt-hash-value".equals(k.getKeyHash())));
    }

    @Test
    void generate_success_publishesAudit() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant(10L)));
        when(ruleSetRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(ruleSet(1L, 10L)));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        ApiKey savedKey = activeKey(7L, "TestKey");
        when(apiKeyRepository.save(any())).thenReturn(savedKey);

        apiKeyService.generate(createReq("TestKey", 1L), 10L);

        verify(auditProducer).publish(eq(AuditAction.APIKEY_CREATED), eq("APIKEY"),
                eq(7L), isNull(), eq("TestKey"), eq(10L), any(), any());
    }

    // ─── REVOKE ──────────────────────────────────────────────

    @Test
    void revoke_whenKeyNotFound_throwsNotFound() {
        when(apiKeyRepository.findByIdAndTenantId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revoke(99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revoke_whenAlreadyRevoked_throwsBadRequest() {
        when(apiKeyRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(revokedKey(1L, "K")));

        assertThatThrownBy(() -> apiKeyService.revoke(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already revoked");
    }

    @Test
    void revoke_success_setsActiveFalseAndNotifies() {
        ApiKey key = activeKey(1L, "LiveKey");
        when(apiKeyRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any())).thenReturn(key);
        when(apiKeyMapper.toDto(key)).thenReturn(ApiKeyResponse.builder().id(1L).name("LiveKey").build());

        apiKeyService.revoke(1L, 10L);

        assertThat(key.isActive()).isFalse();
        verify(auditProducer).publish(eq(AuditAction.APIKEY_REVOKED), eq("APIKEY"),
                eq(1L), any(), any(), eq(10L), any(), any());
        verify(notificationProducer).publish(contains("révoquée"), contains("LiveKey"), any(), eq(10L), eq(1L), eq("APIKEY"));
    }

    // ─── DELETE ──────────────────────────────────────────────

    @Test
    void delete_whenKeyIsActive_throwsBadRequest() {
        when(apiKeyRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(activeKey(1L, "ActiveKey")));

        assertThatThrownBy(() -> apiKeyService.delete(1L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("active");
    }

    @Test
    void delete_whenKeyIsRevoked_deletesSuccessfully() {
        ApiKey key = revokedKey(1L, "OldKey");
        when(apiKeyRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(key));

        apiKeyService.delete(1L, 10L);

        verify(apiKeyRepository).delete(key);
        verify(auditProducer).publish(eq(AuditAction.APIKEY_DELETED), eq("APIKEY"),
                eq(1L), eq("OldKey"), isNull(), eq(10L), any(), any());
    }

    // ─── REGENERATE ──────────────────────────────────────────

    @Test
    void regenerate_revokesOldKeyAndCreatesNewKey() {
        ApiKey oldKey = activeKey(1L, "OldKey");
        when(apiKeyRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(oldKey));
        when(passwordEncoder.encode(any())).thenReturn("new-hash");
        ApiKey newKey = activeKey(2L, "OldKey"); // same name, new id
        when(apiKeyRepository.save(any()))
                .thenReturn(oldKey)   // first save (revoke)
                .thenReturn(newKey);  // second save (create new)

        ApiKeyCreatedResponse result = apiKeyService.regenerate(1L, 10L);

        // Old key must have been revoked
        assertThat(oldKey.isActive()).isFalse();
        // New key was created with raw key in response
        assertThat(result.getRawKey()).startsWith("raas_");
        assertThat(result.getName()).isEqualTo("OldKey");
    }

    // ─── GET ALL / GET BY ID ──────────────────────────────────

    @Test
    void getAll_delegatesToRepository() {
        when(apiKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(apiKeyMapper.toDtoList(any())).thenReturn(List.of());

        apiKeyService.getAll(10L);

        verify(apiKeyRepository).findAllByTenantIdOrderByCreatedAtDesc(10L);
    }

    @Test
    void getById_whenWrongTenant_throwsNotFound() {
        when(apiKeyRepository.findByIdAndTenantId(5L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.getById(5L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}