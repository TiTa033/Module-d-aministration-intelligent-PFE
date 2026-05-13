package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
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
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ChatService {
    private static final Set<String> PROJECT_CONTEXT_KEYWORDS = Set.of(
            "raas", "rule", "regle", "regles", "ruleset", "rule set",
            "condition", "conditions", "action", "actions",
            "strategie", "strategy", "evaluation", "evaluer",
            "first_match", "all_match", "score",
            "tenant", "organisation", "api", "cle",
            "playground", "version", "rollback",
            "import", "export", "documentation",
            "chatbot", "insight", "audit", "notification",
            "activer", "desactiver", "archiver", "creer",
            "modifier", "supprimer", "comment", "pourquoi",
            "qu est", "quelle", "quel", "aide", "help",
            "expliqu", "montr", "liste", "affich"
    );

    private static final String OUT_OF_SCOPE_REPLY = """
            Je suis limité au contexte de ce projet (plateforme RaaS, RuleSets, règles, conditions/actions, stratégies, API keys, évaluation, versions, import/export, documentation).
            Reformulez votre question en lien avec ces sujets et je vous aide immédiatement.
            """;

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
                    String preview = chatMessageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(conv.getId())
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

        List<ChatMessage> recent = chatMessageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(conversation.getId())
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
        String reply;
        if (!isProjectScopedQuestion(userPrompt)) {
            reply = OUT_OF_SCOPE_REPLY;
        } else {
            String systemPrompt = buildSystemPrompt(user, tenantId, request.getRuleSetId());
            try {
                reply = llmClient.chat(systemPrompt, history, userPrompt);
            } catch (Exception e) {
                reply = "Je ne peux pas joindre le modèle IA pour le moment. "
                        + "Vérifiez la configuration de la clé API LLM et réessayez. "
                        + "Détail technique: " + sanitizeLlmError(e);
            }
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
        return chatConversationRepository.findByIdAndTenantIdAndUserId(conversationId, tenantId, user.getId())
                .orElseThrow(() -> new BadRequestException("Conversation introuvable."));
    }

    private String buildSystemPrompt(User user, Long tenantId, Long ruleSetId) {
        StringJoiner rsJoiner = new StringJoiner(", ");
        ruleSetRepository.findAllByTenantWithFilters(
                        tenantId, "", null, org.springframework.data.domain.PageRequest.of(0, 20))
                .forEach(rs -> rsJoiner.add(rs.getName() + " (" + rs.getStatus() + ")"));

        String focusedRuleSet = "Aucun RuleSet ciblé.";
        if (ruleSetId != null) {
            RuleSet rs = ruleSetRepository.findByIdAndTenantId(ruleSetId, tenantId).orElse(null);
            if (rs != null) {
                focusedRuleSet = "RuleSet ciblé: " + rs.getName() + " (stratégie=" + rs.getEvaluationStrategy() + ", statut=" + rs.getStatus() + ")";
            }
        }

        return """
                Tu es l'assistant de la plateforme RaaS (Rules as a Service).
                Tu aides des utilisateurs métier à comprendre les RuleSets et la plateforme.
                Réponds en français, de manière concise, pratique et exacte.
                Si une information est inconnue, dis-le clairement et propose la prochaine action.
                N'accepte PAS les questions hors contexte projet.
                Si la question ne concerne pas ce projet, réponds uniquement:
                "Je suis limité au contexte de ce projet (RaaS, RuleSets, règles, stratégies, API keys, évaluation, versions, import/export, documentation)."
                
                Contexte utilisateur:
                - rôle: %s
                - tenant: %s
                - RuleSets disponibles: %s
                - %s
                """.formatted(
                user.getRole(),
                user.getTenant().getName(),
                rsJoiner.length() == 0 ? "(aucun)" : rsJoiner.toString(),
                focusedRuleSet
        );
    }

    private boolean isProjectScopedQuestion(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return true;
        }
        String normalized = java.text.Normalizer
                .normalize(prompt.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return PROJECT_CONTEXT_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String sanitizeLlmError(Exception e) {
        String raw = e == null ? null : e.getMessage();
        if (raw == null || raw.isBlank()) {
            return "erreur LLM non détaillée";
        }
        String normalized = raw.replaceAll("(?i)sk-[a-z0-9_\\-]+", "[redacted-key]");
        normalized = normalized.replaceAll("(?i)gsk_[a-z0-9_\\-]+", "[redacted-key]");
        normalized = normalized.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 220) {
            normalized = normalized.substring(0, 220) + "...";
        }
        return normalized;
    }

    private String toPreview(String content) {
        if (content == null || content.isBlank()) {
            return "(message vide)";
        }
        String normalized = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }
}
