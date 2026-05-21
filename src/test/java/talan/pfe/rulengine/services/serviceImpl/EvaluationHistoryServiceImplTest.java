package talan.pfe.rulengine.services.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.repositories.EvaluationRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class

EvaluationHistoryServiceImplTest {

    @Mock EvaluationRequestRepository evaluationRequestRepository;

    EvaluationHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EvaluationHistoryServiceImpl(evaluationRequestRepository, new ObjectMapper());
    }

    private EvaluationRequest evalRequest(Long id, Long tenantId, boolean withResult) {
        Tenant tenant = Tenant.builder().id(tenantId).name("Acme").build();
        RuleSet rs = RuleSet.builder().id(5L).name("CreditRule").build();
        ApiKey apiKey = ApiKey.builder().name("TestKey").keyPrefix("raas_test").build();

        EvaluationRequest req = EvaluationRequest.builder()
                .id(id).requestedAt(LocalDateTime.now())
                .tenant(tenant).ruleSet(rs).apiKey(apiKey)
                .inputPayload("{\"income\": 60000}")
                .build();

        if (withResult) {
            EvaluationResult res = EvaluationResult.builder()
                    .evaluatedAt(LocalDateTime.now())
                    .strategyUsed(EvaluationStrategy.FIRST_MATCH)
                    .totalScore(85.0).executionTimeMs(120L)
                    .outputPayload("{\"result\": \"approved\"}")
                    .matchedRules("[{\"ruleId\": 1}, {\"ruleId\": 2}]")
                    .build();
            req.setResult(res);
        }

        return req;
    }

    // ─── GET ALL ─────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getAll_returnsMappedPageWithSummaries() {
        Pageable pageable = PageRequest.of(0, 20);
        EvaluationRequest req = evalRequest(1L, 10L, true);

        when(evaluationRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req)));

        PageResponse<EvaluationHistoryResponse> result = service.getAll(10L, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        EvaluationHistoryResponse summary = result.getContent().get(0);
        assertThat(summary.getId()).isEqualTo(1L);
        assertThat(summary.getRuleSetName()).isEqualTo("CreditRule");
        assertThat(summary.getTotalScore()).isEqualTo(85.0);
        assertThat(summary.getStrategyUsed()).isEqualTo(EvaluationStrategy.FIRST_MATCH);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_matchedRulesCountParsedFromJson() {
        Pageable pageable = PageRequest.of(0, 20);
        EvaluationRequest req = evalRequest(1L, 10L, true);

        when(evaluationRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req)));

        PageResponse<EvaluationHistoryResponse> result = service.getAll(10L, null, null, null, pageable);

        // matchedRules JSON has 2 items
        assertThat(result.getContent().get(0).getMatchedRulesCount()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_returnsZeroMatchedRulesWhenResultIsNull() {
        Pageable pageable = PageRequest.of(0, 20);
        EvaluationRequest req = evalRequest(2L, 10L, false);

        when(evaluationRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req)));

        PageResponse<EvaluationHistoryResponse> result = service.getAll(10L, null, null, null, pageable);

        assertThat(result.getContent().get(0).getMatchedRulesCount()).isZero();
        assertThat(result.getContent().get(0).getTotalScore()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_forcesSortByRequestedAtDesc() {
        Pageable pageable = PageRequest.of(0, 20);
        when(evaluationRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAll(10L, null, null, null, pageable);

        verify(evaluationRequestRepository).findAll(any(Specification.class),
                argThat((Pageable p) -> p.getSort().getOrderFor("requestedAt") != null &&
                        p.getSort().getOrderFor("requestedAt").isDescending()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_returnsEmptyPageWhenNoHistory() {
        Pageable pageable = PageRequest.of(0, 20);
        when(evaluationRequestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<EvaluationHistoryResponse> result = service.getAll(10L, 5L, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ─── GET BY ID ───────────────────────────────────────────

    @Test
    void getById_returnsDetailResponseWithPayloads() {
        EvaluationRequest req = evalRequest(1L, 10L, true);
        when(evaluationRequestRepository.findByIdAndTenantId(1L, 10L)).thenReturn(Optional.of(req));

        EvaluationDetailResponse detail = service.getById(1L, 10L);

        assertThat(detail.getId()).isEqualTo(1L);
        assertThat(detail.getRuleSetName()).isEqualTo("CreditRule");
        assertThat(detail.getInputPayload()).isEqualTo("{\"income\": 60000}");
        assertThat(detail.getOutputPayload()).isEqualTo("{\"result\": \"approved\"}");
        assertThat(detail.getMatchedRules()).isEqualTo("[{\"ruleId\": 1}, {\"ruleId\": 2}]");
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(evaluationRequestRepository.findByIdAndTenantId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Evaluation not found with id: 999");
    }

    @Test
    void getById_returnsNullPayloadsWhenNoResult() {
        EvaluationRequest req = evalRequest(3L, 10L, false);
        when(evaluationRequestRepository.findByIdAndTenantId(3L, 10L)).thenReturn(Optional.of(req));

        EvaluationDetailResponse detail = service.getById(3L, 10L);

        assertThat(detail.getOutputPayload()).isNull();
        assertThat(detail.getMatchedRules()).isNull();
        assertThat(detail.getTotalScore()).isNull();
    }

    @Test
    void getById_mapsApiKeyAndTenantCorrectly() {
        EvaluationRequest req = evalRequest(4L, 10L, true);
        when(evaluationRequestRepository.findByIdAndTenantId(4L, 10L)).thenReturn(Optional.of(req));

        EvaluationDetailResponse detail = service.getById(4L, 10L);

        assertThat(detail.getApiKeyName()).isEqualTo("TestKey");
        assertThat(detail.getApiKeyPrefix()).isEqualTo("raas_test");
        assertThat(detail.getTenantId()).isEqualTo(10L);
        assertThat(detail.getTenantName()).isEqualTo("Acme");
    }
}