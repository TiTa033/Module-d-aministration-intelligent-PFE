package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import talan.pfe.rulengine.dtos.request.ChatRequest;
import talan.pfe.rulengine.dtos.response.ChatConversationSummaryResponse;
import talan.pfe.rulengine.dtos.response.ChatMessageResponse;
import talan.pfe.rulengine.dtos.response.ChatResponse;
import talan.pfe.rulengine.entites.*;
import talan.pfe.rulengine.enums.ChatRole;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.repositories.ChatConversationRepository;
import talan.pfe.rulengine.repositories.ChatMessageRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock CurrentUserResolver currentUserResolver;
    @Mock ChatConversationRepository chatConversationRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock RuleSetRepository ruleSetRepository;
    @Mock LlmClient llmClient;

    @InjectMocks ChatService chatService;

    private Tenant tenant(Long id) {
        return Tenant.builder().id(id).name("Acme").build();
    }

    private User userWithTenant(Long tenantId) {
        return User.builder().id(1L).email("a@test.com").role(Role.ADMIN)
                .tenant(tenant(tenantId)).active(true).build();
    }

    private User userWithoutTenant() {
        return User.builder().id(1L).email("a@test.com").role(Role.ADMIN)
                .tenant(null).active(true).build();
    }

    private ChatConversation conversation(Long id, User user) {
        return ChatConversation.builder()
                .id(id).tenant(user.getTenant()).user(user)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ChatMessage msg(Long id, ChatConversation conv, ChatRole role, String content) {
        return ChatMessage.builder()
                .id(id).conversation(conv).role(role).content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─── listConversations ────────────────────────────────────

    @Test
    void listConversations_whenNoTenant_throwsBadRequest() {
        when(currentUserResolver.requireUser()).thenReturn(userWithoutTenant());

        assertThatThrownBy(() -> chatService.listConversations())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    void listConversations_returnsSummariesWithPreview() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation conv = conversation(1L, user);
        when(chatConversationRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(10L, 1L))
                .thenReturn(List.of(conv));
        ChatMessage lastMsg = msg(1L, conv, ChatRole.USER, "Hello world");
        when(chatMessageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(lastMsg));

        List<ChatConversationSummaryResponse> result = chatService.listConversations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConversationId()).isEqualTo(1L);
        assertThat(result.get(0).getLastMessagePreview()).isEqualTo("Hello world");
    }

    @Test
    void listConversations_emptyConversationShowsPlaceholder() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation conv = conversation(2L, user);
        when(chatConversationRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(10L, 1L))
                .thenReturn(List.of(conv));
        when(chatMessageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(2L))
                .thenReturn(List.of());

        List<ChatConversationSummaryResponse> result = chatService.listConversations();

        assertThat(result.get(0).getLastMessagePreview()).isEqualTo("(conversation vide)");
    }

    @Test
    void listConversations_longMessageIsTruncatedTo80Chars() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation conv = conversation(3L, user);
        when(chatConversationRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(10L, 1L))
                .thenReturn(List.of(conv));
        String longContent = "A".repeat(200);
        ChatMessage lastMsg = msg(1L, conv, ChatRole.USER, longContent);
        when(chatMessageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(3L))
                .thenReturn(List.of(lastMsg));

        List<ChatConversationSummaryResponse> result = chatService.listConversations();

        assertThat(result.get(0).getLastMessagePreview()).endsWith("...");
        assertThat(result.get(0).getLastMessagePreview().length()).isLessThanOrEqualTo(83);
    }

    // ─── getConversationMessages ──────────────────────────────

    @Test
    void getConversationMessages_whenNoTenant_throwsBadRequest() {
        when(currentUserResolver.requireUser()).thenReturn(userWithoutTenant());

        assertThatThrownBy(() -> chatService.getConversationMessages(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    void getConversationMessages_returnsMessageList() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation conv = conversation(1L, user);
        when(chatConversationRepository.findByIdAndTenantIdAndUserId(1L, 10L, 1L))
                .thenReturn(Optional.of(conv));
        ChatMessage m1 = msg(1L, conv, ChatRole.USER, "Hello");
        ChatMessage m2 = msg(2L, conv, ChatRole.ASSISTANT, "Hi!");
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(m1, m2));

        List<ChatMessageResponse> result = chatService.getConversationMessages(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
        assertThat(result.get(0).getRole()).isEqualTo("USER");
        assertThat(result.get(1).getRole()).isEqualTo("ASSISTANT");
    }

    // ─── chat ─────────────────────────────────────────────────

    @Test
    void chat_whenNoTenant_throwsBadRequest() {
        when(currentUserResolver.requireUser()).thenReturn(userWithoutTenant());
        ChatRequest req = new ChatRequest();
        req.setMessage("hello");

        assertThatThrownBy(() -> chatService.chat(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    void chat_createsNewConversationWhenNoConversationId() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation newConv = conversation(5L, user);
        when(chatConversationRepository.save(any())).thenReturn(newConv);
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(5L))
                .thenReturn(List.of());
        when(ruleSetRepository.findAllByTenantWithFilters(eq(10L), eq(""), isNull(),
                any(PageRequest.class))).thenReturn(org.springframework.data.domain.Page.empty());
        when(llmClient.chat(any(), any(), any())).thenReturn("Bonjour!");

        ChatRequest req = new ChatRequest();
        req.setMessage("Hello AI");

        ChatResponse response = chatService.chat(req);

        assertThat(response.getConversationId()).isEqualTo(5L);
        assertThat(response.getReply()).isEqualTo("Bonjour!");
        verify(chatConversationRepository).save(any());
        verify(chatMessageRepository, times(2)).save(any()); // user + assistant
    }

    @Test
    void chat_usesExistingConversation() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation existingConv = conversation(3L, user);
        when(chatConversationRepository.findByIdAndTenantIdAndUserId(3L, 10L, 1L))
                .thenReturn(Optional.of(existingConv));
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(3L))
                .thenReturn(List.of());
        when(ruleSetRepository.findAllByTenantWithFilters(eq(10L), eq(""), isNull(),
                any(PageRequest.class))).thenReturn(org.springframework.data.domain.Page.empty());
        when(llmClient.chat(any(), any(), any())).thenReturn("Bien sûr!");

        ChatRequest req = new ChatRequest();
        req.setConversationId(3L);
        req.setMessage("Follow-up question");

        ChatResponse response = chatService.chat(req);

        assertThat(response.getConversationId()).isEqualTo(3L);
        verify(chatConversationRepository, never()).save(any());
    }

    @Test
    void chat_whenLlmFails_replyContainsErrorMessage() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation newConv = conversation(7L, user);
        when(chatConversationRepository.save(any())).thenReturn(newConv);
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of());
        when(ruleSetRepository.findAllByTenantWithFilters(eq(10L), eq(""), isNull(),
                any(PageRequest.class))).thenReturn(org.springframework.data.domain.Page.empty());
        when(llmClient.chat(any(), any(), any())).thenThrow(new RuntimeException("LLM unavailable"));

        ChatRequest req = new ChatRequest();
        req.setMessage("Test");

        ChatResponse response = chatService.chat(req);

        assertThat(response.getReply()).isEqualTo("LLM unavailable");
    }

    @Test
    void chat_withRuleSetId_includesFocusedRuleSetInSystemPrompt() {
        User user = userWithTenant(10L);
        when(currentUserResolver.requireUser()).thenReturn(user);
        ChatConversation newConv = conversation(9L, user);
        when(chatConversationRepository.save(any())).thenReturn(newConv);
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of());
        RuleSet rs = RuleSet.builder().id(5L).name("CreditRS")
                .evaluationStrategy(EvaluationStrategy.FIRST_MATCH)
                .status(talan.pfe.rulengine.enums.RuleSetStatus.ACTIVE).build();
        when(ruleSetRepository.findAllByTenantWithFilters(eq(10L), eq(""), isNull(),
                any(PageRequest.class))).thenReturn(org.springframework.data.domain.Page.empty());
        when(ruleSetRepository.findByIdAndTenantId(5L, 10L)).thenReturn(Optional.of(rs));
        when(llmClient.chat(any(), any(), any())).thenReturn("Doc available!");

        ChatRequest req = new ChatRequest();
        req.setMessage("Explain this ruleset");
        req.setRuleSetId(5L);

        ChatResponse response = chatService.chat(req);

        assertThat(response.getReply()).isEqualTo("Doc available!");
        verify(ruleSetRepository).findByIdAndTenantId(5L, 10L);
    }
}