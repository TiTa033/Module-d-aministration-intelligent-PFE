package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.ChatMessage;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop12ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<ChatMessage> findTop1ByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
