package talan.pfe.rulengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
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
import talan.pfe.rulengine.security.ApiKeyAuthenticationFilter;
import talan.pfe.rulengine.security.JwtAuthenticationFilter;
import talan.pfe.rulengine.services.serviceImpl.ChatService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ChatController")
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ChatService chatService;
    @MockBean ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test @DisplayName("POST /api/chat → 200 OK")
    void chat_returns200() throws Exception {
        when(chatService.chat(any())).thenReturn(
                ChatResponse.builder().conversationId(1L).reply("Voici la réponse.").build());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Qu'est ce qu'un RuleSet ?"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/chat/conversations → 200 OK")
    void listConversations_returns200() throws Exception {
        when(chatService.listConversations()).thenReturn(List.of(
                ChatConversationSummaryResponse.builder().conversationId(1L).lastMessagePreview("...").build()));

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/chat/conversations/{id}/messages → 200 OK")
    void getMessages_returns200() throws Exception {
        when(chatService.getConversationMessages(1L)).thenReturn(List.of(
                ChatMessageResponse.builder().id(1L).role("USER").content("Bonjour").build()));

        mockMvc.perform(get("/api/chat/conversations/1/messages"))
                .andExpect(status().isOk());
    }
}
