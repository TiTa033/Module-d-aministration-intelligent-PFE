package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.EvaluateRequest;
import talan.pfe.rulengine.dtos.response.EvaluateResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.ApiKeyRepository;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.security.ApiClientPrincipal;
import talan.pfe.rulengine.services.EvaluationService;
import talan.pfe.rulengine.util.InputFieldPath;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final RuleRepository ruleRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final EvaluationRequestRepository evaluationRequestRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public EvaluateResponse evaluate(EvaluateRequest request,
                                     ApiClientPrincipal principal) {
        long start = System.currentTimeMillis();

        ApiKey apiKey = apiKeyRepository.findById(principal.getApiKeyId())
                .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

        RuleSet ruleSet = apiKey.getRuleSet();
        if (ruleSet == null) {
            throw new BadRequestException("API key is not linked to a RuleSet");
        }
        if (ruleSet.getTenant() == null
                || !Objects.equals(ruleSet.getTenant().getId(), principal.getTenantId())) {
            throw new BadRequestException("API key does not belong to this tenant");
        }

        if (ruleSet.getStatus() != RuleSetStatus.ACTIVE) {
            throw new BadRequestException(
                    "Only ACTIVE RuleSets can be evaluated via API");
        }

        EvaluationStrategy strategy = ruleSet.getEvaluationStrategy();

        Map<String, Object> inputMap = objectMapper.convertValue(
                request.getInput(), new TypeReference<>() {});

        List<Rule> rules = ruleRepository.findAllByRuleSetIdOrderByPriorityAsc(
                ruleSet.getId());
        for (Rule r : rules) {
            r.getConditions().size();
            r.getActions().size();
        }

        validateInputAgainstRuleSet(request.getInput(), rules);

        List<Rule> matching = new ArrayList<>();
        for (Rule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            if (ruleMatches(rule, inputMap)) {
                matching.add(rule);
            }
        }

        List<Rule> applied = applyStrategy(strategy, matching);
        Map<String, Object> outputMap = mergeActions(applied);
        double totalScore = matching.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0)
                .sum();

        JsonNode matchedNode = objectMapper.valueToTree(
                applied.stream().map(this::toMatchedSummary).toList());

        String inputJson = toJson(request.getInput());
        String outputJson = toJson(outputMap);
        String matchedJson = toJson(matchedNode);

        EvaluationRequest evalRequest = EvaluationRequest.builder()
                .inputPayload(inputJson)
                .tenant(apiKey.getTenant())
                .ruleSet(ruleSet)
                .apiKey(apiKey)
                .build();

        EvaluationResult result = EvaluationResult.builder()
                .outputPayload(outputJson)
                .matchedRules(matchedJson)
                .executionTimeMs(System.currentTimeMillis() - start)
                .strategyUsed(strategy)
                .totalScore(totalScore)
                .evaluationRequest(evalRequest)
                .build();
        evalRequest.setResult(result);

        EvaluationRequest saved = evaluationRequestRepository.save(evalRequest);

        return EvaluateResponse.builder()
                .evaluationRequestId(saved.getId())
                .ruleSetId(ruleSet.getId())
                .ruleSetName(ruleSet.getName())
                .strategyUsed(strategy)
                .output(objectMapper.valueToTree(outputMap))
                .matchedRules(matchedNode)
                .totalScore(totalScore)
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
    }

    private void validateInputAgainstRuleSet(JsonNode input, List<Rule> rules) {
        if (input == null || !input.isObject()) {
            throw new BadRequestException("input must be a JSON object");
        }

        // Build expected types per field from all conditions.
        Map<String, DataType> expectedType = new LinkedHashMap<>();
        for (Rule rule : rules) {
            for (RuleCondition c : rule.getConditions()) {
                if (c.getField() == null || c.getField().isBlank()) continue;
                DataType t = c.getValueType();
                if (t == null) continue;
                expectedType.putIfAbsent(c.getField(), t);
            }
        }

        Set<String> providedFields = new LinkedHashSet<>();
        collectLeafPaths(input, "", providedFields);

        List<String> unknown = providedFields.stream()
                .filter(f -> !expectedType.containsKey(f))
                .toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException(
                    "Unknown field(s) in input: " + String.join(", ", unknown)
                            + ". Allowed fields: " + String.join(", ", expectedType.keySet()));
        }

        List<String> missing = expectedType.keySet().stream()
                .filter(f -> !providedFields.contains(f))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "Missing field(s) in input: " + String.join(", ", missing)
                            + ". Required fields: " + String.join(", ", expectedType.keySet()));
        }

        List<String> typeErrors = new ArrayList<>();
        for (String field : providedFields) {
            DataType t = expectedType.get(field);
            if (t == null) continue;
            JsonNode node = getNodeByPath(input, field);
            if (node == null || node.isNull()) {
                continue; // let condition evaluation handle nulls (IS_NULL / IS_NOT_NULL)
            }
            if (!isValidType(node, t)) {
                typeErrors.add(field + " expected " + t + " but got " + node.getNodeType());
            }
        }
        if (!typeErrors.isEmpty()) {
            throw new BadRequestException("Invalid input type(s): " + String.join("; ", typeErrors));
        }
    }

    private void collectLeafPaths(JsonNode node, String prefix, Set<String> out) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                String next = prefix.isBlank() ? e.getKey() : prefix + "." + e.getKey();
                collectLeafPaths(e.getValue(), next, out);
            });
            return;
        }
        // arrays treated as leaf
        out.add(prefix);
    }

    private JsonNode getNodeByPath(JsonNode root, String path) {
        JsonNode cur = root;
        for (String p : path.split("\\\\.")) {
            if (cur == null) return null;
            cur = cur.get(p);
        }
        return cur;
    }

    private boolean isValidType(JsonNode node, DataType t) {
        return switch (t) {
            case STRING -> node.isTextual();
            case NUMBER -> node.isNumber();
            case BOOLEAN -> node.isBoolean();
            case DATE -> node.isTextual() && isIsoDate(node.asText());
        };
    }

    private boolean isIsoDate(String s) {
        try {
            java.time.LocalDate.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Serialization error: " + e.getMessage());
        }
    }

    private Map<String, Object> toMatchedSummary(Rule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("priority", r.getPriority());
        m.put("score", r.getScore());
        return m;
    }

    private List<Rule> applyStrategy(EvaluationStrategy strategy,
                                     List<Rule> matching) {
        if (matching.isEmpty()) {
            return List.of();
        }
        return switch (strategy) {
            case FIRST_MATCH -> List.of(matching.get(0));
            case ALL_MATCH -> List.copyOf(matching);
            case SCORE_BASED -> List.of(pickHighestScoreRule(matching));
        };
    }

    private Rule pickHighestScoreRule(List<Rule> matching) {
        Rule best = matching.get(0);
        int bestScore = score(best);
        for (int i = 1; i < matching.size(); i++) {
            Rule r = matching.get(i);
            int s = score(r);
            if (s > bestScore) {
                bestScore = s;
                best = r;
            }
        }
        return best;
    }

    private int score(Rule r) {
        return r.getScore() != null ? r.getScore() : 0;
    }

    private Map<String, Object> mergeActions(List<Rule> rules) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Rule rule : rules) {
            for (RuleAction action : rule.getActions()) {
                out.put(action.getOutputKey(), action.execute());
            }
        }
        return out;
    }

    private boolean ruleMatches(Rule rule, Map<String, Object> input) {
        List<RuleCondition> conditions = rule.getConditions();
        if (conditions.isEmpty()) {
            return true;
        }
        LogicOperator op = rule.getLogicOperator();
        if (op == LogicOperator.AND) {
            return conditions.stream().allMatch(c -> conditionMatches(c, input));
        }
        return conditions.stream().anyMatch(c -> conditionMatches(c, input));
    }

    private boolean conditionMatches(RuleCondition condition,
                                     Map<String, Object> input) {
        Object value = InputFieldPath.resolve(input, condition.getField());
        return condition.evaluate(value);
    }
}
