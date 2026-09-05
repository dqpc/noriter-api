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
            char d = StairsShared.dirAt(seed, i);
            now = now.plusMillis(150);
            s = game.apply(s, d == 'L' ? "a" : "b", Map.of("dir", String.valueOf(d)), now);
        }
        var before = ((StairsShared.State) s).energyAt(now);
        assertThat(before).isLessThan(100);
        char d = StairsShared.dirAt(seed, first);
        s = game.apply(s, d == 'L' ? "a" : "b", Map.of("dir", String.valueOf(d)), now);
        assertThat(((StairsShared.State) s).energy()).isEqualTo(100.0);
        assertThat(s.score()).isEqualTo(first);
    }
}
