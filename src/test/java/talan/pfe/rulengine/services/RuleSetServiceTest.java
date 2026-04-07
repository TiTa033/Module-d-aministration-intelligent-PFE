package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateRuleSetRequest;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.mappers.RuleSetMapper;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.serviceImpl.RuleSetServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleSetServiceTest {

    @Mock private RuleSetRepository ruleSetRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private RuleSetMapper ruleSetMapper;
    @Mock private RuleSetVersioningService ruleSetVersioningService;

    private RuleSetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleSetServiceImpl(
                ruleSetRepository, tenantRepository, ruleSetMapper,
                ruleSetVersioningService);
        doNothing().when(ruleSetVersioningService)
                .recordSnapshot(any(), any(), any());
    }

    @Test
    void create_shouldReturnResponse() {
        Tenant tenant = Tenant.builder().id(1L).name("T1").slug("t1").build();
        RuleSet rs = RuleSet.builder().id(10L).name("RS1").tenant(tenant).build();
        RuleSetResponse dto = RuleSetResponse.builder().id(10L).name("RS1").tenantId(1L).tenantName("T1").build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(ruleSetRepository.existsByNameAndTenantId("RS1", 1L)).thenReturn(false);
        when(ruleSetRepository.save(any(RuleSet.class))).thenReturn(rs);
        when(ruleSetMapper.toDto(rs)).thenReturn(dto);

        CreateRuleSetRequest req = new CreateRuleSetRequest("RS1", "desc", EvaluationStrategy.FIRST_MATCH);
        assertNotNull(service.create(req, 1L));
    }
}

