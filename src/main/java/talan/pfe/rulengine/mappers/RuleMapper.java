package talan.pfe.rulengine.mappers;

import org.mapstruct.*;
import talan.pfe.rulengine.dtos.response.RuleResponse;
import talan.pfe.rulengine.entites.Rule;

import java.util.List;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RuleMapper {

    @Mapping(source = "ruleSet.id",   target = "ruleSetId")
    @Mapping(source = "ruleSet.name", target = "ruleSetName")
    @Mapping(source = "logicOperator", target = "logicOperator",
            qualifiedByName = "operatorToString")
    @Mapping(target = "totalConditions",
            expression = "java(rule.getConditions().size())")
    @Mapping(target = "totalActions",
            expression = "java(rule.getActions().size())")
    RuleResponse toDto(Rule rule);
    List<RuleResponse> toDtoList(List<Rule> rules);
    @Named("operatorToString")
    default String operatorToString(
            talan.pfe.rulengine.enums.LogicOperator operator) {
        return operator != null ? operator.name() : null;
    }
}