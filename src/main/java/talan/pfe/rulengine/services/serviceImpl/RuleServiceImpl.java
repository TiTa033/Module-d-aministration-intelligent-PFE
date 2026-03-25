package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.*;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.*;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleServiceImpl implements talan.pfe.rulengine.services.RuleService {

    private final RuleRepository ruleRepository;
    private final RuleSetRepository ruleSetRepository;

    // ─── CREATE ─────────────────────────────────────────────
    @Transactional
    public RuleResponse create(UUID ruleSetId, UUID tenantId,
                               CreateRuleRequest request) {
        RuleSet ruleSet = findRuleSetOrThrow(ruleSetId, tenantId);

        // Cannot add rules to an ARCHIVED ruleset
        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot add rules to an archived RuleSet");
        }

        // Check name uniqueness within ruleset
        if (ruleRepository.existsByNameAndRuleSetId(
                request.getName(), ruleSetId)) {
            throw new ConflictException(
                    "A Rule with name '" + request.getName() +
                            "' already exists in this RuleSet");
        }

        // Check priority uniqueness within ruleset
        if (ruleRepository.existsByPriorityAndRuleSetId(
                request.getPriority(), ruleSetId)) {
            throw new ConflictException(
                    "A Rule with priority " + request.getPriority() +
                            " already exists in this RuleSet");
        }

        Rule rule = Rule.builder()
                .name(request.getName())
                .description(request.getDescription())
                .priority(request.getPriority())
                .logicOperator(request.getLogicOperator())
                .score(request.getScore())
                .ruleSet(ruleSet)
                .build();

        return RuleResponse.from(ruleRepository.save(rule));
    }

    // ─── GET ALL ────────────────────────────────────────────
    public PageResponse<RuleResponse> getAll(
            UUID ruleSetId, UUID tenantId, RuleFilterRequest filter) {

        findRuleSetOrThrow(ruleSetId, tenantId);

        String searchParam = (filter.getSearch() == null) ? "" : filter.getSearch();

        Boolean enabledParam = null;
        if (filter.getEnabled() != null && !filter.getEnabled().isBlank()) {
            enabledParam = Boolean.parseBoolean(filter.getEnabled());
        }

        Sort sort = filter.getSortDir().equalsIgnoreCase("desc")
                ? Sort.by(filter.getSortBy()).descending()
                : Sort.by(filter.getSortBy()).ascending();

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<RuleResponse> resultPage = ruleRepository
                .findAllByRuleSetWithFilters(
                        ruleSetId, searchParam, enabledParam, pageable)
                .map(RuleResponse::from);

        return PageResponse.from(resultPage);
    }

    // ─── GET ALL LIST (no pagination — for evaluation engine) ──
    public List<RuleResponse> getAllList(UUID ruleSetId, UUID tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        return ruleRepository
                .findAllByRuleSetIdOrderByPriorityAsc(ruleSetId)
                .stream()
                .map(RuleResponse::from)
                .toList();
    }

    // ─── GET BY ID ──────────────────────────────────────────
    public RuleResponse getById(UUID ruleSetId, UUID id, UUID tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        return RuleResponse.from(findRuleOrThrow(id, ruleSetId));
    }

    // ─── UPDATE ─────────────────────────────────────────────
    @Transactional
    public RuleResponse update(UUID ruleSetId, UUID id,
                               UUID tenantId, UpdateRuleRequest request) {
        RuleSet ruleSet = findRuleSetOrThrow(ruleSetId, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot update rules in an archived RuleSet");
        }

        Rule rule = findRuleOrThrow(id, ruleSetId);

        // Check name uniqueness excluding current
        if (ruleRepository.existsByNameAndRuleSetIdAndIdNot(
                request.getName(), ruleSetId, id)) {
            throw new ConflictException(
                    "A Rule with name '" + request.getName() +
                            "' already exists in this RuleSet");
        }

        // Check priority uniqueness excluding current
        if (ruleRepository.existsByPriorityAndRuleSetIdAndIdNot(
                request.getPriority(), ruleSetId, id)) {
            throw new ConflictException(
                    "A Rule with priority " + request.getPriority() +
                            " already exists in this RuleSet");
        }

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setPriority(request.getPriority());
        rule.setLogicOperator(request.getLogicOperator());
        rule.setScore(request.getScore());

        return RuleResponse.from(ruleRepository.save(rule));
    }

    // ─── ENABLE ─────────────────────────────────────────────
    @Transactional
    public RuleResponse enable(UUID ruleSetId, UUID id, UUID tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        Rule rule = findRuleOrThrow(id, ruleSetId);

        if (rule.isEnabled()) {
            throw new BadRequestException("Rule is already enabled");
        }

        rule.setEnabled(true);
        return RuleResponse.from(ruleRepository.save(rule));
    }

    // ─── DISABLE ────────────────────────────────────────────
    @Transactional
    public RuleResponse disable(UUID ruleSetId, UUID id, UUID tenantId) {
        findRuleSetOrThrow(ruleSetId, tenantId);
        Rule rule = findRuleOrThrow(id, ruleSetId);

        if (!rule.isEnabled()) {
            throw new BadRequestException("Rule is already disabled");
        }

        rule.setEnabled(false);
        return RuleResponse.from(ruleRepository.save(rule));
    }

    // ─── DELETE ─────────────────────────────────────────────
    @Transactional
    public void delete(UUID ruleSetId, UUID id, UUID tenantId) {
        RuleSet ruleSet = findRuleSetOrThrow(ruleSetId, tenantId);

        if (ruleSet.getStatus() == RuleSetStatus.ARCHIVED) {
            throw new BadRequestException(
                    "Cannot delete rules from an archived RuleSet");
        }

        Rule rule = findRuleOrThrow(id, ruleSetId);
        ruleRepository.delete(rule);
    }

    // ─── PRIVATE HELPERS ────────────────────────────────────
    private RuleSet findRuleSetOrThrow(UUID ruleSetId, UUID tenantId) {
        return ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RuleSet not found with id: " + ruleSetId));
    }

    private Rule findRuleOrThrow(UUID id, UUID ruleSetId) {
        return ruleRepository.findByIdAndRuleSetId(id, ruleSetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rule not found with id: " + id));
    }
}