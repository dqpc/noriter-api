package games.noriter.api.score.infra;

import games.noriter.api.score.domain.GamePlay;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePlayRepository extends JpaRepository<GamePlay, Long> {
    List<GamePlay> findByGameIdOrderByCreatedAtAsc(String gameId);
}
