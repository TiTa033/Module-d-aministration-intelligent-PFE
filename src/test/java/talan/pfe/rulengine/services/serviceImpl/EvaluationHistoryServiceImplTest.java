package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import talan.pfe.rulengine.dtos.response.EvaluationDetailResponse;
import talan.pfe.rulengine.dtos.response.EvaluationHistoryResponse;
import talan.pfe.rulengine.dtos.response.PageResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluationHistoryServiceImpl")
class EvaluationHistoryServiceImplTest {

    @Mock EvaluationRequestRepository evaluationRequestRepository;

    @InjectMocks EvaluationHistoryServiceImpl service;

    // Real ObjectMapper — needed for JSON parsing in the service
    ObjectMapper objectMapper = new ObjectMapper();

    private Tenant tenant;
    private RuleSet ruleSet;
    private ApiKey apiKey;
    private EvaluationRequest evalRequest;
    private EvaluationResult evalResult;

    @BeforeEach
    void setUp() {
        // Inject real objectMapper into service
        try {
            var field = EvaluationHistoryServiceImpl.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(service, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        tenant = Tenant.builder().id(1L).name("BankCorp").build();
        ruleSet = RuleSet.builder().id(10L).name("Credit RS")
                .status(RuleSetStatus.ACTIVE)
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .tenant(tenant).rules(new ArrayList<>()).build();
        apiKey = ApiKey.builder().id(2L).name("MyKey").keyPrefix("raas_ABC")
                .tenant(tenant).ruleSet(ruleSet).active(true).build();

        evalResult = EvaluationResult.builder()
                .strategyUsed(EvaluationStrategy.FIRST_MATCH)
                .totalScore(10.0)
                .executionTimeMs(5L)
                .matchedRules("[{\"id\":1,\"name\":\"High Income\"}]")
                .outputPayload("{\"decision\":\"APPROVED\"}")
                .evaluatedAt(LocalDateTime.now())
                .build();

        evalRequest = EvaluationRequest.builder()
                .id(100L)
                .inputPayload("{\"amount\":1500}")
                .requestedAt(LocalDateTime.now())
                .tenant(tenant).ruleSet(ruleSet).apiKey(apiKey)
                .result(evalResult)
                .build();
        evalResult.setEvaluationRequest(evalRequest);
    }

    @Test
    @DisplayName("getAll() should return paginated evaluation history with matched rules count")
    void getAll_returnsMappedHistory() {
        Pageable pageable = PageRequest.of(0, 10);
        when(evaluationRequestRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(evalRequest)));

        PageResponse<EvaluationHistoryResponse> result = service.getAll(1L, 10L, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        EvaluationHistoryResponse resp = result.getContent().get(0);
        assertThat(resp.getId()).isEqualTo(100L);
        assertThat(resp.getRuleSetName()).isEqualTo("Credit RS");
        assertThat(resp.getMatchedRulesCount()).isEqualTo(1);
        assertThat(resp.getTotalScore()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("getAll() should return 0 matched rules when result is null")
    void getAll_nullResult_zeroMatchedRules() {
        evalRequest = EvaluationRequest.builder()
                .id(101L).inputPayload("{}")
                .requestedAt(LocalDateTime.now())
                .tenant(tenant).ruleSet(ruleSet).apiKey(apiKey)
                .result(null).build();

        Pageable pageable = PageRequest.of(0, 10);
        when(evaluationRequestRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(evalRequest)));

        PageResponse<EvaluationHistoryResponse> result = service.getAll(1L, null, null, null, pageable);
        assertThat(result.getContent().get(0).getMatchedRulesCount()).isZero();
    }

    @Test
    @DisplayName("getById() should return full evaluation detail")
    void getById_returnsDetail() {
        when(evaluationRequestRepository.findByIdAndTenantId(100L, 1L))
                .thenReturn(Optional.of(evalRequest));

        EvaluationDetailResponse result = service.getById(100L, 1L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getInputPayload()).isEqualTo("{\"amount\":1500}");
        assertThat(result.getOutputPayload()).isEqualTo("{\"decision\":\"APPROVED\"}");
        assertThat(result.getMatchedRules()).isEqualTo("[{\"id\":1,\"name\":\"High Income\"}]");
    }

    @Test
    @DisplayName("getById() should throw ResourceNotFoundException when not found")
    void getById_notFound() {
        when(evaluationRequestRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
