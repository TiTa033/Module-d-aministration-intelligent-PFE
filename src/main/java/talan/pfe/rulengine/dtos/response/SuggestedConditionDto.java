package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuggestedConditionDto {
    private String field;
    private String operator;
    private String value;
    private String valueType;
}
