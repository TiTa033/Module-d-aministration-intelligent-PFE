package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateRuleRequest;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.LogicOperator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.mappers.RuleMapper;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.serviceImpl.RuleServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock private RuleRepository ruleRepository;
    @Mock private RuleSetRepository ruleSetRepository;
    @Mock private RuleMapper ruleMapper;
    @Mock private RuleSetVersioningService ruleSetVersioningService;

    private RuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleServiceImpl(
                ruleRepository, ruleSetRepository, ruleMapper,
                ruleSetVersioningService);
        doNothing().when(ruleSetVersioningService)
                .recordSnapshot(any(), any(), any());
    }

    @Test
    void create_shouldReturnResponse() {
        RuleSet rs = RuleSet.builder().id(10L).status(RuleSetStatus.DRAFT).build();
        Rule rule = Rule.builder().id(100L).name("R1").priority(1).logicOperator(LogicOperator.AND).ruleSet(rs).build();
        RuleResponse dto = RuleResponse.builder().id(100L).name("R1").ruleSetId(10L).build();

        when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(rs));
        when(ruleRepository.existsByNameAndRuleSetId("R1", 10L)).thenReturn(false);
        when(ruleRepository.existsByPriorityAndRuleSetId(1, 10L)).thenReturn(false);
        when(ruleRepository.save(any(Rule.class))).thenReturn(rule);
        when(ruleMapper.toDto(rule)).thenReturn(dto);

        CreateRuleRequest req = new CreateRuleRequest("R1", "desc", 1, LogicOperator.AND, null);
        assertNotNull(service.create(10L, 1L, req));
    }
}

