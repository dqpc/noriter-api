package games.noriter.api.word.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 추측으로 인정하는 단어. 자모 6개가 키. */
@Entity
public class WordDictionary {

    @Id
    @Column(length = 6)
    private String jamo;

    @Column(nullable = false, length = 8)
    private String word;

    private String meaning;

    protected WordDictionary() {}

    public String getJamo() { return jamo; }
    public String getWord() { return word; }
    public String getMeaning() { return meaning; }
}
