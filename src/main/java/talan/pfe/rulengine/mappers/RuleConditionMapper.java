package talan.pfe.rulengine.mappers;

import org.mapstruct.Mapper;
import talan.pfe.rulengine.dtos.response.RuleConditionResponse;
import talan.pfe.rulengine.entites.RuleCondition;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RuleConditionMapper {

    RuleConditionResponse toDto(RuleCondition condition);

    List<RuleConditionResponse> toDtoList(List<RuleCondition> conditions);
}