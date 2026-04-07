package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import talan.pfe.rulengine.dtos.request.CreateApiKeyRequest;
import talan.pfe.rulengine.dtos.response.ApiKeyCreatedResponse;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;
import talan.pfe.rulengine.entites.ApiKey;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.mappers.ApiKeyMapper;
import talan.pfe.rulengine.repositories.ApiKeyRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.serviceImpl.ApiKeyServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private RuleSetRepository ruleSetRepository;
    @Mock private ApiKeyMapper apiKeyMapper;
    @Mock private PasswordEncoder passwordEncoder;

    private ApiKeyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyServiceImpl(
                apiKeyRepository, tenantRepository, ruleSetRepository, apiKeyMapper, passwordEncoder);
    }

    @Test
    void generate_shouldCreateKeyAndReturnRawKey() {
        Long tenantId = 1L;
        Tenant tenant = Tenant.builder().id(tenantId).name("T1").slug("t1").build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        RuleSet rs = RuleSet.builder().id(10L).name("RS").tenant(tenant).build();
        when(ruleSetRepository.findByIdAndTenantId(10L, tenantId)).thenReturn(Optional.of(rs));

        CreateApiKeyRequest req = new CreateApiKeyRequest(
                "My integration", 10L, LocalDateTime.now().plusDays(30));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        ArgumentCaptor<String> rawKeyCaptor = ArgumentCaptor.forClass(String.class);

        ApiKey saved = ApiKey.builder()
                .id(10L)
                .name(req.getName())
                .keyHash("hash")
                .active(true)
                .expiresAt(req.getExpiresAt())
                .tenant(tenant)
                .ruleSet(rs)
                .createdAt(LocalDateTime.now())
                .build();

        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(saved);

        ApiKeyCreatedResponse response = service.generate(req, tenantId);

        assertNotNull(response);
        assertNotNull(response.getRawKey());
        assertFalse(response.getRawKey().isBlank());
        assertNotNull(response.getKeyPrefix());
        assertEquals(tenantId, response.getTenantId());
        assertEquals(req.getName(), response.getName());

        verify(apiKeyRepository).save(captor.capture());
        verify(passwordEncoder).encode(rawKeyCaptor.capture());

        ApiKey toSave = captor.getValue();
        assertEquals(req.getName(), toSave.getName());
        assertEquals(tenant, toSave.getTenant());
        assertEquals(rs, toSave.getRuleSet());
        assertEquals(req.getExpiresAt(), toSave.getExpiresAt());
        assertTrue(toSave.isActive());
        assertEquals("hash", toSave.getKeyHash());

        // keyPrefix is derived from rawKey.substring(0, 12)
        String rawKeyUsed = rawKeyCaptor.getValue();
        assertEquals(rawKeyUsed, response.getRawKey());
        assertEquals(rawKeyUsed.substring(0, 12), response.getKeyPrefix());
    }

    @Test
    void generate_shouldThrowWhenTenantMissing() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.generate(new CreateApiKeyRequest("x", 10L, null), 1L));
    }

    @Test
    void getAll_shouldReturnDtos() {
        Long tenantId = 1L;
        List<ApiKey> keys = List.of(
                ApiKey.builder().id(1L).name("k1").active(true).build(),
                ApiKey.builder().id(2L).name("k2").active(false).build()
        );
        when(apiKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(keys);

        List<ApiKeyResponse> dtos = List.of(
                ApiKeyResponse.builder().id(1L).name("k1").active(true).build(),
                ApiKeyResponse.builder().id(2L).name("k2").active(false).build()
        );
        when(apiKeyMapper.toDtoList(keys)).thenReturn(dtos);

        List<ApiKeyResponse> result = service.getAll(tenantId);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void revoke_shouldDeactivateKey() {
        Long tenantId = 1L;
        Long keyId = 10L;
        ApiKey apiKey = ApiKey.builder()
                .id(keyId)
                .name("k")
                .active(true)
                .tenant(Tenant.builder().id(tenantId).build())
                .build();

        when(apiKeyRepository.findByIdAndTenantId(keyId, tenantId))
                .thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(apiKeyMapper.toDto(any(ApiKey.class)))
                .thenAnswer(inv -> {
                    ApiKey k = inv.getArgument(0);
                    return ApiKeyResponse.builder()
                            .id(k.getId())
                            .name(k.getName())
                            .active(k.isActive())
                            .tenantId(tenantId)
                            .build();
                });

        ApiKeyResponse response = service.revoke(keyId, tenantId);
        assertNotNull(response);
        assertFalse(response.isActive());
    }
}

