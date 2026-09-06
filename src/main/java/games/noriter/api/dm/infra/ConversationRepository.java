package games.noriter.api.dm.infra;

import games.noriter.api.dm.domain.Conversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByDmKey(String dmKey);
}
