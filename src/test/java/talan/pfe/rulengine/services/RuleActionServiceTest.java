package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.RuleActionRequest;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleAction;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.ActionType;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.mappers.RuleActionMapper;
import talan.pfe.rulengine.repositories.RuleActionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.serviceImpl.RuleActionServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleActionServiceTest {

    @Mock private RuleSetRepository ruleSetRepository;
    @Mock private RuleRepository ruleRepository;
    @Mock private RuleActionRepository ruleActionRepository;
    @Mock private RuleActionMapper ruleActionMapper;

    private RuleActionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleActionServiceImpl(ruleSetRepository, ruleRepository, ruleActionRepository, ruleActionMapper);
    }

    @Test
    void create_shouldReturnResponse() {
        RuleSet rs = RuleSet.builder().id(10L).status(RuleSetStatus.DRAFT).build();
        Rule rule = Rule.builder().id(100L).ruleSet(rs).build();
        RuleAction action = RuleAction.builder().id(1000L).rule(rule).actionType(ActionType.APPROVE).outputKey("k").outputValue("v").build();
        RuleActionResponse dto = RuleActionResponse.builder().id(1000L).actionType(ActionType.APPROVE).outputKey("k").outputValue("v").build();

        when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(100L, 10L)).thenReturn(Optional.of(rule));
        when(ruleActionRepository.save(any(RuleAction.class))).thenReturn(action);
        when(ruleActionMapper.toDto(action)).thenReturn(dto);

        RuleActionRequest request = new RuleActionRequest();
        request.setActionType(ActionType.APPROVE);
        request.setOutputKey("k");
        request.setOutputValue("v");

        assertNotNull(service.create(10L, 100L, 1L, request));
    }
}

