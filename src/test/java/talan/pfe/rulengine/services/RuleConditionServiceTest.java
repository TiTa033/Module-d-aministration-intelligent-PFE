package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.RuleConditionRequest;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.entites.Rule;
import talan.pfe.rulengine.entites.RuleCondition;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.mappers.RuleConditionMapper;
import talan.pfe.rulengine.repositories.RuleConditionRepository;
import talan.pfe.rulengine.repositories.RuleRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.services.serviceImpl.RuleConditionServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleConditionServiceTest {

    @Mock private RuleSetRepository ruleSetRepository;
    @Mock private RuleRepository ruleRepository;
    @Mock private RuleConditionRepository ruleConditionRepository;
    @Mock private RuleConditionMapper ruleConditionMapper;

    private RuleConditionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleConditionServiceImpl(ruleSetRepository, ruleRepository, ruleConditionRepository, ruleConditionMapper);
    }

    @Test
    void create_shouldReturnResponse() {
        RuleSet rs = RuleSet.builder().id(10L).status(RuleSetStatus.DRAFT).build();
        Rule rule = Rule.builder().id(100L).ruleSet(rs).build();
        RuleCondition cond = RuleCondition.builder().id(1000L).rule(rule).field("age").operator(Operator.GREATER_OR_EQUAL).value("18").valueType(DataType.NUMBER).build();
        RuleConditionResponse dto = RuleConditionResponse.builder().id(1000L).field("age").operator(Operator.GREATER_OR_EQUAL).value("18").valueType(DataType.NUMBER).build();

        when(ruleSetRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(rs));
        when(ruleRepository.findByIdAndRuleSetId(100L, 10L)).thenReturn(Optional.of(rule));
        when(ruleConditionRepository.save(any(RuleCondition.class))).thenReturn(cond);
        when(ruleConditionMapper.toDto(cond)).thenReturn(dto);

        RuleConditionRequest request = new RuleConditionRequest();
        request.setField("age");
        request.setOperator(Operator.GREATER_OR_EQUAL);
        request.setValue("18");
        request.setValueType(DataType.NUMBER);

        assertNotNull(service.create(10L, 100L, 1L, request));
    }
}

