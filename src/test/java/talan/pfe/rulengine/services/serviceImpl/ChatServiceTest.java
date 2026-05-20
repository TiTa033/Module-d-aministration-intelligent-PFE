package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import talan.pfe.rulengine.dtos.request.ChatRequest;
import talan.pfe.rulengine.dtos.response.ChatResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.ChatRole;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.repositories.*;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService")
class ChatServiceTest {

    @Mock CurrentUserResolver currentUserResolver;
    @Mock ChatConversationRepository chatConversationRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock LlmClient llmClient;

    @InjectMocks ChatService service;

    private User userWithTenant;
    private User userWithoutTenant;
    private Tenant tenant;
    private ChatConversation conversation;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("BankCorp").build();

        userWithTenant = User.builder()
                .id(10L).email("admin@bank.com")
                .role(Role.ADMIN).tenant(tenant).build();

        userWithoutTenant = User.builder()
                .id(11L).email("global@raas.com")
                .role(Role.GLOBAL_ADMIN).tenant(null).build();

        conversation = ChatConversation.builder()
                .id(100L).tenant(tenant).user(userWithTenant).build();
    }

    // ─── TENANT GUARD ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant context guard")
    class TenantGuard {

        @Test
        @DisplayName("chat() should throw BadRequestException when user has no tenant")
        void chat_noTenant_throwsBadRequest() {
            when(currentUserResolver.requireUser()).thenReturn(userWithoutTenant);

            ChatRequest req = new ChatRequest();
            req.setMessage("Comment créer une règle ?");

            assertThatThrownBy(() -> service.chat(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tenant context");
        }

        @Test
        @DisplayName("listConversations() should throw BadRequestException when user has no tenant")
        void listConversations_noTenant_throwsBadRequest() {
            when(currentUserResolver.requireUser()).thenReturn(userWithoutTenant);

            assertThatThrownBy(() -> service.listConversations())
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tenant context");
        }
    }

    // ─── OUT-OF-SCOPE DETECTION ──────────────────────────────────────────────

    @Nested
    @DisplayName("Out-of-scope question detection")
    class OutOfScope {

        @Test
        @DisplayName("should return canned reply for clearly out-of-scope question")
        void chat_outOfScope_returnsCannedReply() {
            when(ruleSetRepository.findAllByTenantWithFilters(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));
            when(currentUserResolver.requireUser()).thenReturn(userWithTenant);
            when(chatConversationRepository.save(any())).thenReturn(conversation);
            when(chatMessageRepository.save(any())).thenReturn(ChatMessage.builder()
                    .id(1L).role(ChatRole.USER).content("Quelle est la capitale de France ?").build());
            when(chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(100L))
                    .thenReturn(new ArrayList<>());
            when(chatMessageRepository.save(any())).thenReturn(mock(ChatMessage.class));

            ChatRequest req = new ChatRequest();
            req.setMessage("Quelle est la capitale de France ?");
            req.setConversationId(null); // new conversation

            ChatResponse response = service.chat(req);

            // LLM should NOT be called for out-of-scope questions
            verify(llmClient, never()).chat(any(), any(), any());
            assertThat(response.getReply()).contains("limité au contexte");
        }

        @Test
        @DisplayName("should call LLM for in-scope question about rules")
        void chat_inScope_callsLlm() throws Exception {
            when(currentUserResolver.requireUser()).thenReturn(userWithTenant);
            when(chatConversationRepository.save(any())).thenReturn(conversation);
            when(chatMessageRepository.save(any())).thenReturn(mock(ChatMessage.class));
            when(chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(100L))
                    .thenReturn(new ArrayList<>());
            when(ruleSetRepository.findAllByTenantWithFilters(eq(1L), any(), any(), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
            when(llmClient.chat(anyString(), anyList(), anyString()))
                    .thenReturn("Voici comment créer une règle...");

            ChatRequest req = new ChatRequest();
            req.setMessage("Comment créer une règle dans le système ?");
            req.setConversationId(null);

            ChatResponse response = service.chat(req);

            verify(llmClient).chat(anyString(), anyList(), anyString());
            assertThat(response.getReply()).contains("créer une règle");
        }

        @Test
        @DisplayName("should return error message when LLM throws exception")
        void chat_llmThrows_returnsErrorMessage() throws Exception {
            when(currentUserResolver.requireUser()).thenReturn(userWithTenant);
            when(chatConversationRepository.save(any())).thenReturn(conversation);
            when(chatMessageRepository.save(any())).thenReturn(mock(ChatMessage.class));
            when(chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(100L))
                    .thenReturn(new ArrayList<>());
            when(ruleSetRepository.findAllByTenantWithFilters(eq(1L), any(), any(), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
            when(llmClient.chat(anyString(), anyList(), anyString()))
                    .thenThrow(new RuntimeException("Groq timeout"));

            ChatRequest req = new ChatRequest();
            req.setMessage("liste les ruleset disponibles");
            req.setConversationId(null);

            ChatResponse response = service.chat(req);
            assertThat(response.getReply()).contains("modèle IA");
        }
    }

    // ─── CONVERSATION RESOLUTION ─────────────────────────────────────────────

    @Nested
    @DisplayName("Conversation resolution")
    class ConversationResolution {

        @Test
        @DisplayName("getConversationMessages() should throw BadRequestException for unknown conversation")
        void getMessages_unknownConversation_throwsBadRequest() {
            when(currentUserResolver.requireUser()).thenReturn(userWithTenant);
            when(chatConversationRepository.findByIdAndTenantIdAndUserId(999L, 1L, 10L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getConversationMessages(999L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Conversation introuvable");
        }

        @Test
        @DisplayName("getConversationMessages() should return messages for valid conversation")
        void getMessages_validConversation_returnsMessages() {
            when(currentUserResolver.requireUser()).thenReturn(userWithTenant);
            when(chatConversationRepository.findByIdAndTenantIdAndUserId(100L, 1L, 10L))
                    .thenReturn(Optional.of(conversation));

            ChatMessage msg = ChatMessage.builder()
                    .id(1L).role(ChatRole.USER)
                    .content("Bonjour").conversation(conversation).build();
            when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(100L))
                    .thenReturn(List.of(msg));

            var result = service.getConversationMessages(100L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContent()).isEqualTo("Bonjour");
        }
    }
}
