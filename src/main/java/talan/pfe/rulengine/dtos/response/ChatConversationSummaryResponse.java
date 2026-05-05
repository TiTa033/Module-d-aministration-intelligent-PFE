package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatConversationSummaryResponse {
    private Long conversationId;
    private String lastMessagePreview;
    private LocalDateTime updatedAt;
}
