package games.noriter.api.score.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * 게임 한 판의 이용 기록. 게스트·솔로도 남기며 통계용이라 삭제하지 않는다.
 * 혼자 하기는 시작 때 행을 만들고(score null, createdAt = 시작 시각) 종료 때 서버가 검증한 점수를 채운다.
 */
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

    /** 혼자 하기 세션 식별자. 추측할 수 없는 무작위 값이라 게스트도 자기 판만 끝낼 수 있다 */
    @Column(length = 32)
    private String token;

    private Long seed;

    private Instant finishedAt;

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

    public static GamePlay startSolo(String gameId, Long userId, String token, long seed, Instant now) {
        var play = new GamePlay(gameId, Mode.SOLO, null, userId, 1, null, null, now);
        play.token = token;
        play.seed = seed;
        return play;
    }

    public void finish(long score, Instant now) {
        this.score = score;
        this.finishedAt = now;
    }

    public boolean isFinished() { return finishedAt != null; }

    public Long getId() { return id; }
    public String getGameId() { return gameId; }
    public Mode getPlayMode() { return playMode; }
    public Long getUserId() { return userId; }
    public int getPlayerCount() { return playerCount; }
    public Long getScore() { return score; }
    public Instant getCreatedAt() { return createdAt; }
    public String getToken() { return token; }
    public Long getSeed() { return seed; }
    public Instant getFinishedAt() { return finishedAt; }
}
