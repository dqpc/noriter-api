package games.noriter.api.score;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "game_score")
class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false, length = 32)
    private String gameId;

    /** user 모듈 엔티티를 직접 참조하지 않고 id 만 보관한다 (모듈 경계). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GameScore() {}

    GameScore(String gameId, Long userId, long score) {
        this.gameId = gameId;
        this.userId = userId;
        this.score = score;
        this.createdAt = Instant.now();
    }

    Long getId() { return id; }
    String getGameId() { return gameId; }
    Long getUserId() { return userId; }
    long getScore() { return score; }
    Instant getCreatedAt() { return createdAt; }
}
