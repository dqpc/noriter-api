package games.noriter.api.dm.infra;

import games.noriter.api.dm.domain.Message;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId, Pageable pageable);
    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);
    long countByConversationIdAndIdGreaterThan(Long conversationId, long lastReadId);
}
