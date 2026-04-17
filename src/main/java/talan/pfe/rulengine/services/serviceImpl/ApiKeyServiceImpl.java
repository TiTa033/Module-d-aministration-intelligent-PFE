package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.CreateApiKeyRequest;
import talan.pfe.rulengine.dtos.response.ApiKeyCreatedResponse;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;
import talan.pfe.rulengine.entites.ApiKey;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.NotifType;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.kafka.NotificationProducer;
import talan.pfe.rulengine.mappers.ApiKeyMapper;
import talan.pfe.rulengine.repositories.ApiKeyRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.ApiKeyService;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final RuleSetRepository ruleSetRepository;
    private final ApiKeyMapper apiKeyMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditProducer auditProducer;
    private final CurrentUserResolver currentUserResolver;
    private final NotificationProducer notificationProducer;

    private static final String KEY_PREFIX = "raas_";
    private static final int KEY_BYTES = 32;

    // ─── GENERATE ───────────────────────────────────────────
    @Override
    @Transactional
    public ApiKeyCreatedResponse generate(CreateApiKeyRequest request, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        RuleSet ruleSet = ruleSetRepository
                .findByIdAndTenantId(request.getRuleSetId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + request.getRuleSetId()));

        String rawKey = generateRawKey();
        String keyHash = passwordEncoder.encode(rawKey);
        String keyPrefix = rawKey.substring(0, 12);

        ApiKey apiKey = ApiKey.builder()
                .name(request.getName())
                .keyHash(keyHash)
                .keyPrefix(rawKey.substring(0, Math.min(12, rawKey.length())))
                .expiresAt(request.getExpiresAt())
                .tenant(tenant)
                .ruleSet(ruleSet)
                .active(true)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        auditProducer.publish(
                AuditAction.APIKEY_CREATED, "APIKEY", saved.getId(),
                null, saved.getName(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return ApiKeyCreatedResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .rawKey(rawKey)
                .keyPrefix(keyPrefix)
                .tenantId(tenantId)
                .ruleSetId(ruleSet.getId())
                .ruleSetName(ruleSet.getName())
                .createdAt(saved.getCreatedAt())
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    // ─── GET ALL ────────────────────────────────────────────
    @Override
    public List<ApiKeyResponse> getAll(Long tenantId) {
        return apiKeyMapper.toDtoList(
                apiKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    // ─── GET BY ID ──────────────────────────────────────────
    @Override
    public ApiKeyResponse getById(Long id, Long tenantId) {
        return apiKeyMapper.toDto(findOrThrow(id, tenantId));
    }

    // ─── REVOKE ─────────────────────────────────────────────
    @Override
    @Transactional
    public ApiKeyResponse revoke(Long id, Long tenantId) {
        ApiKey apiKey = findOrThrow(id, tenantId);

        if (!apiKey.isActive()) {
            throw new BadRequestException("API Key is already revoked");
        }

        apiKey.setActive(false);
        ApiKeyResponse saved = apiKeyMapper.toDto(apiKeyRepository.save(apiKey));

        auditProducer.publish(
                AuditAction.APIKEY_REVOKED, "APIKEY", id,
                null, apiKey.getName(),
                tenantId, currentUserResolver.getCurrentUserId(), null);
        notificationProducer.publish(
                "Clé API révoquée",
                "La clé API '" + apiKey.getName() + "' a été révoquée",
                NotifType.ERROR, tenantId, id, "APIKEY");

        return saved;
    }

    // ─── REGENERATE ─────────────────────────────────────────
    @Override
    @Transactional
    public ApiKeyCreatedResponse regenerate(Long id, Long tenantId) {
        ApiKey apiKey = findOrThrow(id, tenantId);

        String rawKey = generateRawKey();
        String keyHash = passwordEncoder.encode(rawKey);
        String keyPrefix = rawKey.substring(0, 12);

        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);

        Tenant tenant = apiKey.getTenant();
        RuleSet ruleSet = apiKey.getRuleSet();

        ApiKey newApiKey = ApiKey.builder()
                .name(apiKey.getName())
                .keyHash(keyHash)
                .keyPrefix(rawKey.substring(0, Math.min(12, rawKey.length())))
                .expiresAt(apiKey.getExpiresAt())
                .tenant(tenant)
                .ruleSet(ruleSet)
                .active(true)
                .build();

        ApiKey saved = apiKeyRepository.save(newApiKey);

        auditProducer.publish(
                AuditAction.APIKEY_REGENERATED, "APIKEY", saved.getId(),
                apiKey.getName(), saved.getName(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return ApiKeyCreatedResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .rawKey(rawKey)
                .keyPrefix(keyPrefix)
                .tenantId(tenantId)
                .ruleSetId(ruleSet.getId())
                .ruleSetName(ruleSet.getName())
                .createdAt(saved.getCreatedAt())
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    // ─── DELETE ─────────────────────────────────────────────
    @Override
    @Transactional
    public void delete(Long id, Long tenantId) {
        ApiKey apiKey = findOrThrow(id, tenantId);

        if (apiKey.isActive()) {
            throw new BadRequestException(
                    "Cannot delete an active API Key. Revoke it first.");
        }

        auditProducer.publish(
                AuditAction.APIKEY_DELETED, "APIKEY", id,
                apiKey.getName(), null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        apiKeyRepository.delete(apiKey);
    }

    // ─── PRIVATE HELPERS ────────────────────────────────────
    private ApiKey findOrThrow(Long id, Long tenantId) {
        return apiKeyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "API Key not found with id: " + id));
    }

    private String generateRawKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
        return KEY_PREFIX + encoded;
    }
}