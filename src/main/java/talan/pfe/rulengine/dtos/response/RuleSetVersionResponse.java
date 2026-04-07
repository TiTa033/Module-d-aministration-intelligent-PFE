package talan.pfe.rulengine.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetVersionResponse {

    private Long id;
    private Integer versionNumber;
    private String changeNote;
    private LocalDateTime createdAt;
    private String createdByEmail;
}
