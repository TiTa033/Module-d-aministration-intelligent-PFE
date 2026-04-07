package talan.pfe.rulengine.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import talan.pfe.rulengine.dtos.request.EvaluateRequest;
import talan.pfe.rulengine.dtos.response.EvaluateResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.*;
import talan.pfe.rulengine.repositories.*;
import talan.pfe.rulengine.security.ApiClientPrincipal;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EvaluationServiceNoMockTest {

    @Autowired private EvaluationService evaluationService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private RuleSetRepository ruleSetRepository;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private RuleConditionRepository ruleConditionRepository;
    @Autowired private RuleActionRepository ruleActionRepository;
    @Autowired private EvaluationRequestRepository evaluationRequestRepository;

    private Tenant tenant;
    private ApiKey apiKey;
    private RuleSet ruleSet;

    @BeforeEach
    void setupData() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("T1")
                .slug("t1-" + System.nanoTime())
                .status(TenantStatus.ACTIVE)
                .build());

        ruleSet = ruleSetRepository.save(RuleSet.builder()
                .tenant(tenant)
                .name("RS")
                .status(RuleSetStatus.ACTIVE)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .build());

        String raw = "raas_" + System.nanoTime() + "_RAW";
        apiKey = apiKeyRepository.save(ApiKey.builder()
                .name("k")
                .tenant(tenant)
                .ruleSet(ruleSet)
                .active(true)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .keyHash(passwordEncoder.encode(raw))
                .keyPrefix(raw.substring(0, Math.min(12, raw.length())))
                .build());

        Rule r1 = ruleRepository.save(Rule.builder()
                .ruleSet(ruleSet)
                .name("R1")
                .priority(1)
                .enabled(true)
                .logicOperator(LogicOperator.AND)
                .score(5)
                .build());
        ruleConditionRepository.save(RuleCondition.builder()
                .rule(r1)
                .field("amount")
                .operator(Operator.GREATER_OR_EQUAL)
                .value("100")
                .valueType(DataType.NUMBER)
                .build());
        ruleActionRepository.save(RuleAction.builder()
                .rule(r1)
                .actionType(ActionType.APPROVE)
                .outputKey("decision")
                .outputValue("APPROVED")
                .build());

        Rule r2 = ruleRepository.save(Rule.builder()
                .ruleSet(ruleSet)
                .name("R2")
                .priority(2)
                .enabled(true)
                .logicOperator(LogicOperator.AND)
                .score(50)
                .build());
        ruleConditionRepository.save(RuleCondition.builder()
                .rule(r2)
                .field("amount")
                .operator(Operator.GREATER_OR_EQUAL)
                .value("100")
                .valueType(DataType.NUMBER)
                .build());
        ruleActionRepository.save(RuleAction.builder()
                .rule(r2)
                .actionType(ActionType.REQUIRE_REVIEW)
                .outputKey("decision")
                .outputValue("REVIEW")
                .build());
    }

    @Test
    void evaluate_firstMatch_usesFirstRule() {
        ApiClientPrincipal principal = new ApiClientPrincipal(tenant.getId(), apiKey.getId());
        EvaluateRequest req = EvaluateRequest.builder()
                .input(objectMapper.valueToTree(Map.of("amount", 150)))
                .build();

        EvaluateResponse res = evaluationService.evaluate(req, principal);

        assertEquals(EvaluationStrategy.FIRST_MATCH, res.getStrategyUsed());
        assertEquals("APPROVED", res.getOutput().get("decision").asText());
        assertEquals(1, res.getMatchedRules().size());
        assertEquals(55.0, res.getTotalScore());
        assertTrue(evaluationRequestRepository.findById(res.getEvaluationRequestId()).isPresent());
    }

    @Test
    void evaluate_unknownField_returnsBadRequest() {
        ApiClientPrincipal principal = new ApiClientPrincipal(tenant.getId(), apiKey.getId());
        EvaluateRequest req = EvaluateRequest.builder()
                .input(objectMapper.valueToTree(Map.of("inmmmcone", 10)))
                .build();

        var ex = assertThrows(
                talan.pfe.rulengine.exception.BadRequestException.class,
                () -> evaluationService.evaluate(req, principal)
        );
        assertTrue(ex.getMessage().contains("Unknown field"));
    }

    @Test
    void evaluate_typeMismatch_returnsBadRequest() {
        ApiClientPrincipal principal = new ApiClientPrincipal(tenant.getId(), apiKey.getId());
        EvaluateRequest req = EvaluateRequest.builder()
                .input(objectMapper.valueToTree(Map.of("amount", true)))
                .build();

        var ex = assertThrows(
                talan.pfe.rulengine.exception.BadRequestException.class,
                () -> evaluationService.evaluate(req, principal)
        );
        assertTrue(ex.getMessage().contains("Invalid input type"));
    }
}

