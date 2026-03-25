package talan.pfe.rulengine.mappers;

import org.mapstruct.Mapper;
import talan.pfe.rulengine.dtos.response.RuleActionResponse;
import talan.pfe.rulengine.entites.RuleAction;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RuleActionMapper {

    RuleActionResponse toDto(RuleAction action);

    List<RuleActionResponse> toDtoList(List<RuleAction> actions);
}