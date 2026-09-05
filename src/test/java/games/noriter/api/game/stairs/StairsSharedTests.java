package games.noriter.api.game.stairs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StairsSharedTests {

    final StairsShared game = new StairsShared();
    final Instant t0 = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void patternMarksItemsWithLowercaseAndNeverBeforeMinStep() {
        var p = StairsShared.pattern(42, 200);
        assertThat(p).hasSize(201);
        for (int i = 0; i < 5; i++) assertThat(Character.isUpperCase(p.charAt(i))).isTrue();
        assertThat(p.chars().anyMatch(Character::isLowerCase)).isTrue();
        assertThat(StairsShared.pattern(42, 200)).isEqualTo(p);
    }

    @Test
    void steppingOnLightningRefillsEnergy() {
        long seed = 42;
        int first = 5;
        while (!StairsShared.itemAt(seed, first)) first++;
        var s = game.start(seed, Map.of("speed", "normal"), List.of("a", "b"), t0);
        Instant now = t0;
        for (int i = 1; i < first; i++) {
            now = now.plusMillis(150);
            s = step(s, seed, i, now);
        }
        var st = (StairsShared.State) s;
        now = now.plusMillis((long) (st.energy() * 0.6 / st.drainRate() * 1000));
        var before = st.energyAt(now);
        assertThat(before).isGreaterThan(0).isLessThan(60);
        s = step(s, seed, first, now);
        assertThat(((StairsShared.State) s).energy()).isEqualTo(100.0);
        assertThat(s.score()).isEqualTo(first);
    }

    /** i번째 계단을 오르는 올바른 입력: 보는 방향과 같으면 CLIMB(b), 다르면 TURN(a) */
    private games.noriter.api.game.SharedState step(games.noriter.api.game.SharedState s, long seed, int i, Instant now) {
        var st = (StairsShared.State) s;
        boolean turn = StairsShared.dirAt(seed, i) != st.facing();
        return game.apply(s, turn ? "a" : "b", Map.of("action", turn ? "TURN" : "CLIMB"), now);
    }

    @Test
    void startsFacingFirstStairAndTurnFlipsFacing() {
        long seed = 7;
        var s = (StairsShared.State) game.start(seed, Map.of(), List.of("a", "b"), t0);
        assertThat(s.facing()).isEqualTo(StairsShared.dirAt(seed, 1));
        var climbed = (StairsShared.State) game.apply(s, "b", Map.of("action", "CLIMB"), t0.plusMillis(100));
        assertThat(climbed.steps()).isEqualTo(1);
        var ignored = (StairsShared.State) game.apply(climbed, "a", Map.of("action", "CLIMB"), t0.plusMillis(200));
        assertThat(ignored.steps()).isEqualTo(1);
        boolean needTurn = StairsShared.dirAt(seed, 2) != climbed.facing();
        var wrong = (StairsShared.State) game.apply(climbed, needTurn ? "b" : "a", Map.of("action", needTurn ? "CLIMB" : "TURN"), t0.plusMillis(200));
        assertThat(wrong.ended()).isTrue();
        assertThat(wrong.fell()).isTrue();
    }
}
