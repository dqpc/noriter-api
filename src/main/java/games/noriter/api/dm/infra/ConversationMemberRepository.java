package games.noriter.api.dm.infra;

import games.noriter.api.dm.domain.ConversationMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
    List<ConversationMember> findByUserId(Long userId);
    List<ConversationMember> findByConversationId(Long conversationId);
    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);
}
