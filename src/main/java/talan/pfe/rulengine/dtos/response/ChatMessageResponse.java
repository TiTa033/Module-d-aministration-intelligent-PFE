package talan.pfe.rulengine.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Long id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
