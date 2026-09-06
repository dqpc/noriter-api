package games.noriter.api.user.infra;

import games.noriter.api.user.domain.Friend;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendRepository extends JpaRepository<Friend, Long> {
    List<Friend> findAllByUserIdOrderByCreatedAtAsc(Long userId);
    Optional<Friend> findByUserIdAndFriendId(Long userId, Long friendId);
}
