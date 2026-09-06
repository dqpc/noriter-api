package games.noriter.api.score.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** 게임 한 판의 이용 기록. 게스트·솔로도 남기며 통계용이라 삭제하지 않는다. */
@Entity
public class GamePlay {

    public enum Mode { SOLO, ROOM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Mode playMode;

    @Column(length = 8)
    private String roomId;

    private Long userId;

    @Column(nullable = false)
    private int playerCount;

    private Long score;

    private Integer finishRank;

    @Column(nullable = false)
    private Instant createdAt;

    protected GamePlay() {}

    public GamePlay(String gameId, Mode playMode, String roomId, Long userId, int playerCount, Long score, Integer finishRank, Instant createdAt) {
        this.gameId = gameId;
        this.playMode = playMode;
        this.roomId = roomId;
        this.userId = userId;
        this.playerCount = playerCount;
        this.score = score;
        this.finishRank = finishRank;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getGameId() { return gameId; }
    public Mode getPlayMode() { return playMode; }
    public Long getUserId() { return userId; }
    public int getPlayerCount() { return playerCount; }
}
