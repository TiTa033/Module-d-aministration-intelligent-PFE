package talan.pfe.rulengine.mappers;

import org.mapstruct.*;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.entites.RuleSet;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RuleSetMapper {

    @Mapping(source = "tenant.id",   target = "tenantId")
    @Mapping(source = "tenant.name", target = "tenantName")
    @Mapping(source = "evaluationStrategy", target = "evaluationStrategy",
            qualifiedByName = "strategyToString")
    @Mapping(source = "status", target = "status",
            qualifiedByName = "statusToString")
    @Mapping(target = "totalRules", expression = "java(ruleSet.getRules().size())")
    RuleSetResponse toDto(RuleSet ruleSet);

    List<RuleSetResponse> toDtoList(List<RuleSet> ruleSets);

    @Named("strategyToString")
    default String strategyToString(
            talan.pfe.rulengine.enums.EvaluationStrategy strategy) {
        return strategy != null ? strategy.name() : null;
    }

    @Named("statusToString")
    default String statusToString(
            talan.pfe.rulengine.enums.RuleSetStatus status) {
        return status != null ? status.name() : null;
    }
}