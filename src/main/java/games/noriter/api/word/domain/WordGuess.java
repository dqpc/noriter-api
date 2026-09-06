package games.noriter.api.word.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** 계정 사용자의 추측 한 번. 결과(시도 횟수)는 클라이언트 말이 아니라 이 기록으로 계산한다. */
@Entity
public class WordGuess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private short seq;

    @Column(nullable = false, length = 6)
    private String jamo;

    @Column(nullable = false)
    private Instant createdAt;

    protected WordGuess() {}

    public WordGuess(Long userId, int number, int seq, String jamo, Instant createdAt) {
        this.userId = userId;
        this.number = number;
        this.seq = (short) seq;
        this.jamo = jamo;
        this.createdAt = createdAt;
    }

    public int getNumber() { return number; }
    public int getSeq() { return seq; }
    public String getJamo() { return jamo; }
}
