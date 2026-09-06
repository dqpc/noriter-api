package games.noriter.api.word.infra;

import games.noriter.api.word.domain.WordGuess;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordGuessRepository extends JpaRepository<WordGuess, Long> {
    List<WordGuess> findByUserIdAndNumberOrderBySeqAsc(Long userId, int number);
}
