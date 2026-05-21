package talan.pfe.rulengine.dtos.request;

import lombok.Data;
import java.util.List;

@Data
public class N8nInsightPayload {
    private String title;
    private String description;
    private Float confidence;
    private String period;
    private List<String> sources;
}
