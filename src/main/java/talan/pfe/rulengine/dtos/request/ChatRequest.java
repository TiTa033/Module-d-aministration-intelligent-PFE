package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    private Long conversationId;
    private Long ruleSetId;

    @NotBlank
    private String message;
}
