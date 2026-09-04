package games.noriter.api.score;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface GameScoreRepository extends JpaRepository<GameScore, Long> {
    List<GameScore> findByGameIdOrderByScoreDescCreatedAtAsc(String gameId, Pageable pageable);
}
