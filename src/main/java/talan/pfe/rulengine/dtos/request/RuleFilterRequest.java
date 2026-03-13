package talan.pfe.rulengine.dtos.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleFilterRequest {
    private String search;
    private String enabled;
    private int page = 0;
    private int size = 10;
    private String sortBy = "priority";
    private String sortDir = "asc";
}