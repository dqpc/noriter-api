package games.noriter.api.score.infra;

import games.noriter.api.score.domain.GameScore;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameScoreRepository extends JpaRepository<GameScore, Long> {
    List<GameScore> findByGameIdOrderByScoreDescCreatedAtAsc(String gameId, Pageable pageable);
}
