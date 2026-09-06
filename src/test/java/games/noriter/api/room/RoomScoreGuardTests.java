package games.noriter.api.room;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.game.GameSpec;
import games.noriter.api.room.domain.Room;
import games.noriter.api.room.domain.Room.ScoreResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomScoreGuardTests {

    static final Instant START = Instant.parse("2026-09-07T00:00:00Z");
    static final GameSpec STAIRS = new GameSpec("stairs", "계단 오르기", 1, 4, 8, null, true, true, Map.of(), Map.of(), false, false,
            new GameSpec.ScoreLimits(40, 8, 1_000));
    static final GameSpec FREE = new GameSpec("free", "한도 없음", 1, 4, 8, null, true, true, Map.of(), Map.of(), false, false);

    Room room;

    @BeforeEach
    void setUp() {
        room = playing(STAIRS);
    }

    private static Room playing(GameSpec spec) {
        var r = new Room("r", spec);
        r.join("a", "A", "rabbit", null);
        r.join("b", "B", "tiger", null);
        r.countdown("a", START, 7);
        r.play();
        return r;
    }

    private long scoreOf(String playerId) {
        return room.snapshot().players().stream().filter(p -> p.id().equals(playerId)).findFirst().orElseThrow().score();
    }

    @Test
    void normalProgressIsAccepted() {
        for (int i = 1; i <= 30; i++) {
            assertThat(room.score("a", i, START.plusMillis(i * 200L))).isEqualTo(ScoreResult.ACCEPTED);
        }
        assertThat(scoreOf("a")).isEqualTo(30);
    }

    @Test
    void scoreBeyondElapsedTimeIsRejectedAndLastValueKept() {
        assertThat(room.score("a", 10, START.plusSeconds(1))).isEqualTo(ScoreResult.ACCEPTED);
        // 1초 지났는데 40칸/초 × (1 + 여유 2)초 = 120 을 넘는 값
        assertThat(room.score("a", 500, START.plusSeconds(1))).isEqualTo(ScoreResult.REJECTED);
        assertThat(scoreOf("a")).isEqualTo(10);
    }

    @Test
    void absoluteCapIsRejectedAndNegativeIsJustADecrease() {
        assertThat(room.score("a", 1_001, START.plusSeconds(600))).isEqualTo(ScoreResult.REJECTED);
        assertThat(room.score("a", -1, START.plusSeconds(1))).isEqualTo(ScoreResult.IGNORED);
        assertThat(scoreOf("a")).isZero();
    }

    @Test
    void decreaseOrRepeatIsIgnoredWithoutError() {
        room.score("a", 20, START.plusSeconds(1));
        assertThat(room.score("a", 15, START.plusSeconds(2))).isEqualTo(ScoreResult.IGNORED);
        assertThat(room.score("a", 20, START.plusSeconds(2))).isEqualTo(ScoreResult.IGNORED);
        assertThat(scoreOf("a")).isEqualTo(20);
    }

    @Test
    void jumpAllowanceGrowsWithTimeSinceLastAcceptedScore() {
        room.score("a", 10, START.plusSeconds(1));
        // 직전 수락 후 0초: 8 과 40×2 중 큰 80 까지만
        assertThat(room.score("a", 100, START.plusSeconds(1))).isEqualTo(ScoreResult.REJECTED);
        // 재접속 등으로 5초 비었으면 40×(5+2)=280 까지 봐준다
        assertThat(room.score("a", 100, START.plusSeconds(6))).isEqualTo(ScoreResult.ACCEPTED);
        assertThat(scoreOf("a")).isEqualTo(100);
    }

    @Test
    void finishWithImplausibleScoreKeepsLastAcceptedButStillFinishes() {
        room.score("a", 12, START.plusSeconds(1));
        assertThat(room.finish("a", 9_000, START.plusSeconds(2))).isEqualTo(ScoreResult.REJECTED);
        var a = room.snapshot().players().get(0);
        assertThat(a.finished()).isTrue();
        assertThat(a.score()).isEqualTo(12);
        assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(room.finish("b", 15, START.plusSeconds(2))).isEqualTo(ScoreResult.ACCEPTED);
        assertThat(room.status()).isEqualTo(RoomStatus.FINISHED);
    }

    @Test
    void rateLimitDropsExcessMessagesWithinASecond() {
        var t = START.plusSeconds(10);
        int accepted = 0;
        for (int i = 1; i <= 30; i++) {
            if (room.score("a", i, t.plusMillis(i * 10L)) == ScoreResult.ACCEPTED) accepted++;
        }
        assertThat(accepted).isEqualTo(Room.SCORE_MESSAGES_PER_SECOND);
        // 다음 1초 창에서는 다시 받는다
        assertThat(room.score("a", 31, t.plus(Duration.ofSeconds(2)))).isEqualTo(ScoreResult.ACCEPTED);
    }

    @Test
    void gameWithoutLimitsAcceptsAnything() {
        room = playing(FREE);
        assertThat(room.score("a", 999_999, START)).isEqualTo(ScoreResult.ACCEPTED);
        assertThat(room.finish("a", 5, START)).isEqualTo(ScoreResult.IGNORED);
        assertThat(scoreOf("a")).isEqualTo(999_999);
    }

    @Test
    void rematchResetsGuardState() {
        room.score("a", 30, START.plusSeconds(1));
        room.finish("a", 30, START.plusSeconds(2));
        room.finish("b", 1, START.plusSeconds(2));
        room.rematch("a");
        room.countdown("a", START.plusSeconds(100), 8);
        room.play();
        assertThat(room.score("a", 30, START.plusSeconds(101))).isEqualTo(ScoreResult.ACCEPTED);
    }
}
