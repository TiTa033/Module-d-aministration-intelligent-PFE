package talan.pfe.rulengine.dtos.request;

import lombok.Data;
import talan.pfe.rulengine.dtos.response.SuggestedRuleDto;

import java.util.List;

@Data
public class CreateRulesFromSuggestionsRequest {
    private Long ruleSetId;
    private List<SuggestedRuleDto> rules;
}
