package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateRulesFromSuggestionsResponse {
    private String status;
    private int rulesCreated;
    private String createdAt;
}
