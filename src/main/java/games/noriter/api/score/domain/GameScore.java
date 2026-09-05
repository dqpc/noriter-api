package games.noriter.api.score.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String gameId;

    /** user 모듈 엔티티를 직접 참조하지 않고 id 만 보관한다 (모듈 경계). */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long score;

    @Column(nullable = false)
    private Instant createdAt;

    protected GameScore() {}

    public GameScore(String gameId, Long userId, long score) {
        this.gameId = gameId;
        this.userId = userId;
        this.score = score;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getGameId() { return gameId; }
    public Long getUserId() { return userId; }
    public long getScore() { return score; }
    public Instant getCreatedAt() { return createdAt; }
}
