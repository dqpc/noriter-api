package games.noriter.api.word.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** 계정 사용자의 하루 결과. attempts 가 null 이면 여섯 번 안에 못 맞힌 것. */
@Entity
public class WordResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int number;

    private Short attempts;

    @Column(nullable = false)
    private boolean hard;

    @Column(nullable = false)
    private Instant createdAt;

    protected WordResult() {}

    public WordResult(Long userId, int number, Integer attempts, boolean hard, Instant createdAt) {
        this.userId = userId;
        this.number = number;
        this.attempts = attempts == null ? null : attempts.shortValue();
        this.hard = hard;
        this.createdAt = createdAt;
    }

    public Long getUserId() { return userId; }
    public int getNumber() { return number; }
    public Integer getAttempts() { return attempts == null ? null : attempts.intValue(); }
    public boolean isHard() { return hard; }
    public boolean won() { return attempts != null; }
}
