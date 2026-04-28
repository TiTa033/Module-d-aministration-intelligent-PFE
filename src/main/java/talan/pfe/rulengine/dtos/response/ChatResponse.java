package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private Long conversationId;
    private String reply;
}
