package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.ChatRequest;
import talan.pfe.rulengine.dtos.response.ChatConversationSummaryResponse;
import talan.pfe.rulengine.dtos.response.ChatMessageResponse;
import talan.pfe.rulengine.dtos.response.ChatResponse;
import talan.pfe.rulengine.entites.ChatConversation;
import talan.pfe.rulengine.entites.ChatMessage;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.ChatRole;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.repositories.ChatConversationRepository;
import talan.pfe.rulengine.repositories.ChatMessageRepository;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.llm.LlmClient;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final CurrentUserResolver currentUserResolver;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RuleSetRepository ruleSetRepository;
    private final LlmClient llmClient;

    @Transactional(readOnly = true)
    public List<ChatConversationSummaryResponse> listConversations() {
        User user = currentUserResolver.requireUser();
        if (user.getTenant() == null) {
            throw new BadRequestException("Tenant context is required for chat.");
        }

        return chatConversationRepository
                .findByTenantIdAndUserIdOrderByUpdatedAtDesc(user.getTenant().getId(), user.getId())
                .stream()
                .map(conv -> {
                    String preview = chatMessageRepository
                            .findTop1ByConversationIdOrderByCreatedAtDesc(conv.getId())
                            .stream()
                            .findFirst()
                            .map(ChatMessage::getContent)
                            .map(this::toPreview)
                            .orElse("(conversation vide)");
                    return ChatConversationSummaryResponse.builder()
                            .conversationId(conv.getId())
                            .lastMessagePreview(preview)
                            .updatedAt(conv.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getConversationMessages(Long conversationId) {
        User user = currentUserResolver.requireUser();
        if (user.getTenant() == null) {
            throw new BadRequestException("Tenant context is required for chat.");
        }

        resolveConversation(conversationId, user.getTenant().getId(), user);
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(msg -> ChatMessageResponse.builder()
                        .id(msg.getId())
                        .role(msg.getRole().name())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public ChatResponse chat(ChatRequest request) {
        User user = currentUserResolver.requireUser();
        if (user.getTenant() == null) {
            throw new BadRequestException("Tenant context is required for chat.");
        }
        Long tenantId = user.getTenant().getId();

        ChatConversation conversation = resolveConversation(request.getConversationId(), tenantId, user);

        ChatMessage userMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(ChatRole.USER)
                .content(request.getMessage())
                .build();
        chatMessageRepository.save(userMessage);

        List<ChatMessage> recent = chatMessageRepository
                .findTop12ByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .toList();

        List<LlmClient.Message> history = recent.stream()
                .filter(m -> m.getRole() != ChatRole.SYSTEM)
                .map(m -> new LlmClient.Message(
                        m.getRole() == ChatRole.USER ? "user" : "assistant",
                        m.getContent()))
                .toList();

        String userPrompt = request.getMessage() == null ? "" : request.getMessage().trim();
        String systemPrompt = buildSystemPrompt(user, tenantId, request.getRuleSetId());
        String reply;
        try {
            reply = llmClient.chat(systemPrompt, history, userPrompt);
        } catch (Exception e) {
            log.error("Chat LLM error: {}", e.getMessage());
            reply = e.getMessage() != null ? e.getMessage() : "Le service IA est temporairement indisponible.";
        }

        ChatMessage assistantMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(ChatRole.ASSISTANT)
                .content(reply)
                .build();
        chatMessageRepository.save(assistantMessage);

        return ChatResponse.builder()
                .conversationId(conversation.getId())
                .reply(reply)
                .build();
    }

    private ChatConversation resolveConversation(Long conversationId, Long tenantId, User user) {
        if (conversationId == null) {
            return chatConversationRepository.save(ChatConversation.builder()
                    .tenant(user.getTenant())
                    .user(user)
                    .build());
        }
        return chatConversationRepository
                .findByIdAndTenantIdAndUserId(conversationId, tenantId, user.getId())
                .orElseThrow(() -> new BadRequestException("Conversation introuvable."));
    }

    private String buildSystemPrompt(User user, Long tenantId, Long ruleSetId) {
        StringJoiner rsJoiner = new StringJoiner(", ");
        ruleSetRepository.findAllByTenantWithFilters(
                        tenantId, "", null, org.springframework.data.domain.PageRequest.of(0, 20))
                .forEach(rs -> rsJoiner.add(rs.getName() + " (" + rs.getStatus() + ")"));

        String focusedRuleSet = "Aucun RuleSet cible.";
        if (ruleSetId != null) {
            RuleSet rs = ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId).orElse(null);
            if (rs != null) {
                focusedRuleSet = "RuleSet cible: " + rs.getName()
                        + " (strategie=" + rs.getEvaluationStrategy()
                        + ", statut=" + rs.getStatus() + ")";
            }
        }

        return """
                Tu es l'assistant de la plateforme RaaS (Rules as a Service).
                Tu aides les utilisateurs a comprendre et utiliser les RuleSets, regles, conditions, actions, strategies d'evaluation, API keys, versions, import/export et la documentation.
                Reponds toujours en francais, de facon concise et professionnelle.
                Si une question est hors du contexte de cette plateforme, decline poliment et invite l'utilisateur a poser une question en rapport avec RaaS.

                Contexte:
                - Role utilisateur: %s
                - Organisation: %s
                - RuleSets disponibles: %s
                - %s
                """.formatted(
                user.getRole(),
                user.getTenant().getName(),
                rsJoiner.length() == 0 ? "(aucun)" : rsJoiner.toString(),
                focusedRuleSet
        );
    }

    private String toPreview(String content) {
        if (content == null || content.isBlank()) return "(message vide)";
        String normalized = content.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }
}
