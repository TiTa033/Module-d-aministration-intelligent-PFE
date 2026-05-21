package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuggestedActionDto {
    private String actionType;
    private String outputKey;
    private String outputValue;
}
