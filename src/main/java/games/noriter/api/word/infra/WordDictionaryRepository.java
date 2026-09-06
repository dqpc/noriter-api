package games.noriter.api.word.infra;

import games.noriter.api.word.domain.WordDictionary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordDictionaryRepository extends JpaRepository<WordDictionary, String> {}
