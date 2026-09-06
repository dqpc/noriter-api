package games.noriter.api.word.infra;

import games.noriter.api.word.domain.WordPuzzle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordPuzzleRepository extends JpaRepository<WordPuzzle, Integer> {}
