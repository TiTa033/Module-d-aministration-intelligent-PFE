package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CollectWithSuggestionsResponse {
    private Map<String, String> collectedData;
    private List<SuggestedRuleDto> suggestedRules;
    private String fetchedAt;
    private String collectionMethod;
    private int tenantsAffected;
    private int newRulesCount;
    private int existingRulesCount;
}
