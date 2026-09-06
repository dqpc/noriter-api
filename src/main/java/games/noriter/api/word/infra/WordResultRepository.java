package games.noriter.api.word.infra;

import games.noriter.api.word.domain.WordResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordResultRepository extends JpaRepository<WordResult, Long> {
    Optional<WordResult> findByUserIdAndNumber(Long userId, int number);
    List<WordResult> findByUserIdOrderByNumberAsc(Long userId);
}
