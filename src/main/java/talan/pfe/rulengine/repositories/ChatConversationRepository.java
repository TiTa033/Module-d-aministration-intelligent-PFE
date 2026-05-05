package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.ChatConversation;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    Optional<ChatConversation> findByIdAndTenantIdAndUserId(Long id, Long tenantId, Long userId);

    List<ChatConversation> findByTenantIdAndUserIdOrderByUpdatedAtDesc(Long tenantId, Long userId);
}
