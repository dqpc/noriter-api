package games.noriter.api.game2048;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class Game2048ReplayTests {

    /** 웹 scripts/replay-cases-2048.ts 가 만든 (seed, 목표, 입력 로그, 점수, 최종 판) */
    record Case(int seed, int target, String moves, long score, int[] board, boolean won, boolean over) {}

    final Game2048Replay replay = new Game2048Replay();

    static List<Case> cases() throws IOException {
        try (var in = Game2048ReplayTests.class.getResourceAsStream("/game2048/replay-cases.json")) {
            return JsonMapper.builder().build().readValue(in, new TypeReference<List<Case>>() {});
        }
    }

    @Test
    void replaysEveryWebCaseToTheSameScoreAndBoard() throws IOException {
        var all = cases();
        assertThat(all).hasSize(1000);
        for (var c : all) {
            var board = new Board2048(c.seed(), c.target());
            for (char code : c.moves().toCharArray()) {
                assertThat(board.step(code)).as("seed %d move %c", c.seed(), code).isTrue();
            }
            assertThat(board.score()).as("seed %d score", c.seed()).isEqualTo(c.score());
            assertThat(board.cells()).as("seed %d board", c.seed()).containsExactly(c.board());
            assertThat(board.won()).as("seed %d won", c.seed()).isEqualTo(c.won());
            assertThat(board.over()).as("seed %d over", c.seed()).isEqualTo(c.over());

            var result = replay.replay(c.seed(), Map.of("target", c.target()), c.moves());
            assertThat(result.score()).isEqualTo(c.score());
            assertThat(result.applied()).isEqualTo(c.moves().length());
            assertThat(result.complete()).isTrue();
        }
    }

    @Test
    void stopsAtFirstInvalidMoveAndKeepsScoreSoFar() throws IOException {
        var c = cases().get(0);
        var half = c.moves().substring(0, c.moves().length() / 2);
        var expected = replay.replay(c.seed(), Map.of("target", c.target()), half);
        var broken = replay.replay(c.seed(), Map.of("target", c.target()), half + "x" + c.moves().substring(half.length()));
        assertThat(broken.score()).isEqualTo(expected.score());
        assertThat(broken.applied()).isEqualTo(half.length());
        assertThat(broken.complete()).isFalse();
    }

    @Test
    void targetComesFromRoomOptionsAsNumberOrText() {
        var numeric = replay.replay(1, Map.of("target", 512), "");
        var text = replay.replay(1, Map.of("target", "512"), "");
        assertThat(numeric.score()).isEqualTo(text.score()).isZero();
        assertThat(replay.replay(1, null, null).complete()).isTrue();
    }

    @Test
    void cutsAbsurdlyLongLogs() {
        var result = replay.replay(1, Map.of(), "0".repeat(Game2048Replay.MAX_MOVES + 5));
        assertThat(result.applied()).isLessThanOrEqualTo(Game2048Replay.MAX_MOVES);
        assertThat(result.complete()).isFalse();
    }
}
