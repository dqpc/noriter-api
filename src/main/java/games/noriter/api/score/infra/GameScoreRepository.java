package games.noriter.api.score.infra;

import games.noriter.api.score.domain.GameScore;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameScoreRepository extends JpaRepository<GameScore, Long> {
    List<GameScore> findByGameIdOrderByScoreDescCreatedAtAsc(String gameId, Pageable pageable);
    List<GameScore> findByUserIdOrderByCreatedAtAsc(Long userId);
    List<GameScore> findByUserIdAndGameId(Long userId, String gameId);
}
