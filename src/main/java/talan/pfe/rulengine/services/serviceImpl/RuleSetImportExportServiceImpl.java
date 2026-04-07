package talan.pfe.rulengine.services.serviceImpl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.RollbackRuleSetRequest;
import talan.pfe.rulengine.dtos.request.RuleSetImportRequest;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.dtos.response.RuleSetValidationResponse;
import talan.pfe.rulengine.dtos.response.RuleSetVersionResponse;
import talan.pfe.rulengine.dtos.rulesetexport.RuleSetExportPackage;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.RuleSetVersion;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.mappers.RuleSetMapper;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.RuleSetVersionRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.RuleSetImportExportService;
import talan.pfe.rulengine.services.RuleSetSnapshotService;
import talan.pfe.rulengine.services.RuleSetVersioningService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleSetImportExportServiceImpl implements RuleSetImportExportService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleSetVersionRepository ruleSetVersionRepository;
    private final TenantRepository tenantRepository;
    private final RuleSetSnapshotService snapshotService;
    private final RuleSetVersioningService ruleSetVersioningService;
    private final RuleSetMapper ruleSetMapper;
    private final Validator validator;

    @Override
    @Transactional(readOnly = true)
    public String exportJson(Long ruleSetId, Long tenantId) {
        RuleSet ruleSet = ruleSetRepository
                .findByIdAndTenantIdWithRules(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));
        for (var r : ruleSet.getRules()) {
            r.getConditions().size();
            r.getActions().size();
        }
        try {
            return snapshotService.toJson(ruleSet);
        } catch (Exception e) {
            throw new IllegalStateException("Export failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public RuleSetResponse importPackage(RuleSetImportRequest request, Long tenantId) {
        RuleSetValidationResponse validation = validatePackage(request, tenantId);
        if (!validation.isValid()) {
            throw new BadRequestException(String.join("; ", validation.getErrors()));
        }

        RuleSetExportPackage pkg = snapshotService.parse(request.getPackageJson());
        String name = request.getTargetName() != null && !request.getTargetName().isBlank()
                ? request.getTargetName()
                : pkg.getName();

        if (request.isFailIfNameExists()
                && ruleSetRepository.existsByNameAndTenantId(name, tenantId)) {
            throw new ConflictException(
                    "A RuleSet named '" + name + "' already exists for this tenant");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        RuleSet ruleSet = RuleSet.builder()
                .name(name)
                .description(pkg.getDescription())
                .evaluationStrategy(EvaluationStrategy.valueOf(pkg.getEvaluationStrategy()))
                .tenant(tenant)
                .status(RuleSetStatus.DRAFT)
                .build();

        RuleSet saved = ruleSetRepository.save(ruleSet);
        snapshotService.applyPackage(saved, pkg);
        saved = ruleSetRepository.save(saved);

        ruleSetVersioningService.recordSnapshot(
                saved.getId(), tenantId, "Imported configuration");
        return ruleSetMapper.toDto(ruleSetRepository.findById(saved.getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public RuleSetValidationResponse validatePackage(RuleSetImportRequest request,
                                                     Long tenantId) {
        List<String> errors = new ArrayList<>();
        RuleSetExportPackage pkg;
        try {
            pkg = snapshotService.parse(request.getPackageJson());
        } catch (Exception e) {
            return RuleSetValidationResponse.builder()
                    .valid(false)
                    .errors(List.of("Invalid JSON structure: " + e.getMessage()))
                    .build();
        }

        if (pkg.getSchemaVersion() == null
                || pkg.getSchemaVersion() < 1
                || pkg.getSchemaVersion() > RuleSetExportPackage.CURRENT_SCHEMA) {
            errors.add("Unsupported schemaVersion: " + pkg.getSchemaVersion());
        }

        Set<ConstraintViolation<RuleSetExportPackage>> violations = validator.validate(pkg);
        for (ConstraintViolation<RuleSetExportPackage> v : violations) {
            errors.add(v.getPropertyPath() + ": " + v.getMessage());
        }

        for (var rule : pkg.getRules()) {
            errors.addAll(validator.validate(rule).stream()
                    .map(v -> "rule." + v.getPropertyPath() + ": " + v.getMessage())
                    .toList());
            if (rule.getConditions() != null) {
                for (var c : rule.getConditions()) {
                    errors.addAll(validator.validate(c).stream()
                            .map(v -> "condition." + v.getPropertyPath() + ": "
                                    + v.getMessage())
                            .toList());
                }
            }
            if (rule.getActions() != null) {
                for (var a : rule.getActions()) {
                    errors.addAll(validator.validate(a).stream()
                            .map(v -> "action." + v.getPropertyPath() + ": "
                                    + v.getMessage())
                            .toList());
                }
            }
        }

        try {
            EvaluationStrategy.valueOf(pkg.getEvaluationStrategy());
        } catch (IllegalArgumentException e) {
            errors.add("Invalid evaluationStrategy: " + pkg.getEvaluationStrategy());
        }
        if (pkg.getStatus() != null && !pkg.getStatus().isBlank()) {
            try {
                RuleSetStatus.valueOf(pkg.getStatus());
            } catch (IllegalArgumentException e) {
                errors.add("Invalid status: " + pkg.getStatus());
            }
        }

        if (request.isFailIfNameExists()) {
            String name = request.getTargetName() != null && !request.getTargetName().isBlank()
                    ? request.getTargetName()
                    : pkg.getName();
            if (ruleSetRepository.existsByNameAndTenantId(name, tenantId)) {
                errors.add("RuleSet name already exists: " + name);
            }
        }

        return RuleSetValidationResponse.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleSetVersionResponse> listVersions(Long ruleSetId, Long tenantId) {
        if (!ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId).isPresent()) {
            throw new ResourceNotFoundException(
                    "RuleSet not found with id: " + ruleSetId);
        }
        return ruleSetVersionRepository.findByRuleSetIdOrderByVersionNumberDesc(ruleSetId)
                .stream()
                .map(this::toVersionResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RuleSetResponse restoreVersion(Long ruleSetId, Long tenantId,
                                          int versionNumber,
                                          RollbackRuleSetRequest request) {
        RuleSet ruleSet = ruleSetRepository
                .findByIdAndTenantIdWithRules(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException("Cannot rollback an archived RuleSet");
        }

        RuleSetVersion version = ruleSetVersionRepository
                .findByRuleSetIdAndVersionNumber(ruleSetId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Version " + versionNumber + " not found for this RuleSet"));

        RuleSetExportPackage pkg;
        try {
            pkg = snapshotService.parse(version.getSnapshot());
        } catch (Exception e) {
            throw new BadRequestException("Stored snapshot is invalid: " + e.getMessage());
        }

        snapshotService.applyPackage(ruleSet, pkg);
        ruleSetRepository.save(ruleSet);

        String note = request != null && request.getChangeNote() != null
                ? request.getChangeNote()
                : "Restored version " + versionNumber;
        ruleSetVersioningService.recordSnapshot(ruleSetId, tenantId, note);

        return ruleSetMapper.toDto(ruleSetRepository.findById(ruleSetId).orElseThrow());
    }

    private RuleSetVersionResponse toVersionResponse(RuleSetVersion v) {
        return RuleSetVersionResponse.builder()
                .id(v.getId())
                .versionNumber(v.getVersionNumber())
                .changeNote(v.getChangeNote())
                .createdAt(v.getCreatedAt())
                .createdByEmail(v.getCreatedBy().getEmail())
                .build();
    }
}
