package games.noriter.api.word.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 정답 순서표. number 는 1부터, 날짜 번호가 개수를 넘으면 처음부터 돈다. */
@Entity
public class WordPuzzle {

    @Id
    private Integer number;

    @Column(nullable = false, length = 6)
    private String jamo;

    @Column(nullable = false, length = 8)
    private String word;

    private String meaning;

    protected WordPuzzle() {}

    public Integer getNumber() { return number; }
    public String getJamo() { return jamo; }
    public String getWord() { return word; }
    public String getMeaning() { return meaning; }
}
