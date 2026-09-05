package games.noriter.api.game.yut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YutGameTests {

    final YutGame game = new YutGame();
    final Instant t0 = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void turnFlowThrowThenMoveThenNextPlayer() {
        var s = (YutState) game.start(7, Map.of("pieces", 2), List.of("a", "b"), t0);
        assertThat(s.currentPlayer()).isEqualTo("a");
        assertThat(s.deadline()).isEqualTo(t0.plusSeconds(30));
        assertThatThrownBy(() -> game.apply(s, "b", Map.of("type", "throw"), t0)).hasMessageContaining("not your turn");

        int guard = 0;
        while (s.phase == YutState.Phase.THROW && guard++ < 20) game.apply(s, "a", Map.of("type", "throw"), t0);
        assertThat(s.phase == YutState.Phase.MOVE || s.currentPlayer().equals("b")).isTrue();
        if (s.phase == YutState.Phase.MOVE) {
            var legal = YutRules.legalMoves(s);
            assertThat(legal).isNotEmpty();
            var mv = legal.get(0);
            game.apply(s, "a", Map.of("type", "move", "pieceId", mv.pieceId(), "result", mv.result().name()), t0);
        }
        if (s.queue.isEmpty() && s.bonusThrows == 0) assertThat(s.currentPlayer()).isEqualTo("b");
    }

    @Test
    void autoPlaysToTheEndWithBots() {
        var s = (YutState) game.start(11, Map.of("pieces", 2), List.of("a", "b", "c"), t0);
        game.leave(s, "a", t0);
        game.leave(s, "b", t0);
        game.leave(s, "c", t0);
        assertThat(s.isBot("a")).isTrue();
        var now = t0;
        int steps = 0;
        while (!s.ended() && steps++ < 5000) {
            now = s.deadline();
            game.auto(s, now);
        }
        assertThat(s.ended()).isTrue();
        assertThat(s.finishedOrder).hasSize(1);
        assertThat(s.ranking()).hasSize(3);
        assertThat(s.ranking().get(0)).isEqualTo(s.finishedOrder.get(0));
        assertThat(s.scores().get(s.ranking().get(0))).isEqualTo(3L);
        assertThat(s.view()).containsKeys("players", "turn", "phase", "legalMoves", "ranking");
    }

    @Test
    void illegalMoveRejected() {
        var s = (YutState) game.start(3, Map.of(), List.of("a", "b"), t0);
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.DO);
        assertThatThrownBy(() -> game.apply(s, "a", Map.of("type", "move", "pieceId", 0, "result", "MO"), t0)).hasMessageContaining("illegal move");
    }
}
