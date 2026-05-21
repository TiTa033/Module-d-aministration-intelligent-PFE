package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import talan.pfe.rulengine.dtos.response.ChatConversationSummaryResponse;
import talan.pfe.rulengine.dtos.response.ChatMessageResponse;
import talan.pfe.rulengine.dtos.response.ChatResponse;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.security.JwtService;
import talan.pfe.rulengine.services.serviceImpl.ChatService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ChatService chatService;
    @MockBean JwtService jwtService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private ChatResponse chatResp() {
        return ChatResponse.builder()
                .conversationId(1L)
                .reply("Voici la réponse à votre question.")
                .build();
    }

    private ChatConversationSummaryResponse convSummary(Long id) {
        return ChatConversationSummaryResponse.builder()
                .conversationId(id)
                .lastMessagePreview("Dernière question posée...")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageResponse msgResp(Long id, String role, String content) {
        return ChatMessageResponse.builder()
                .id(id).role(role).content(content)
                .createdAt(LocalDateTime.now()).build();
    }

    // ─── CHAT ────────────────────────────────────────────────

    @Test
    void chat_returns200WithAnswer() throws Exception {
        when(chatService.chat(any())).thenReturn(chatResp());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "Comment ajouter une règle ?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(1))
                .andExpect(jsonPath("$.reply").value("Voici la réponse à votre question."));
    }

    @Test
    void chat_returns400WhenNoTenantContext() throws Exception {
        when(chatService.chat(any()))
                .thenThrow(new BadRequestException("Tenant context is required for chat."));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Bonjour"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tenant context is required for chat."));
    }

    // ─── LIST CONVERSATIONS ───────────────────────────────────

    @Test
    void listConversations_returns200WithList() throws Exception {
        when(chatService.listConversations())
                .thenReturn(List.of(convSummary(1L), convSummary(2L)));

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].conversationId").value(1))
                .andExpect(jsonPath("$[1].conversationId").value(2));
    }

    @Test
    void listConversations_returnsEmptyListWhenNone() throws Exception {
        when(chatService.listConversations()).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── GET CONVERSATION MESSAGES ────────────────────────────

    @Test
    void getConversationMessages_returns200WithMessages() throws Exception {
        when(chatService.getConversationMessages(1L)).thenReturn(List.of(
                msgResp(1L, "USER", "Comment ajouter une règle ?"),
                msgResp(2L, "ASSISTANT", "Pour ajouter une règle...")));

        mockMvc.perform(get("/api/chat/conversations/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
    }

    @Test
    void getConversationMessages_returns404WhenConversationNotFound() throws Exception {
        when(chatService.getConversationMessages(999L))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(get("/api/chat/conversations/999/messages"))
                .andExpect(status().isNotFound());
    }
}