package talan.pfe.rulengine.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import talan.pfe.rulengine.dtos.request.ChatRequest;
import talan.pfe.rulengine.dtos.response.ChatConversationSummaryResponse;
import talan.pfe.rulengine.dtos.response.ChatMessageResponse;
import talan.pfe.rulengine.dtos.response.ChatResponse;
import talan.pfe.rulengine.services.serviceImpl.ChatService;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "Contextual assistant for platform users")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Ask the assistant")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(request));
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "List current user chat conversations")
    public ResponseEntity<List<ChatConversationSummaryResponse>> listConversations() {
        return ResponseEntity.ok(chatService.listConversations());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'ADMIN', 'MANAGER', 'VIEWER')")
    @Operation(summary = "Get full messages of one conversation")
    public ResponseEntity<List<ChatMessageResponse>> getConversationMessages(@PathVariable Long conversationId) {
        return ResponseEntity.ok(chatService.getConversationMessages(conversationId));
    }
}
