package games.noriter.api.game.yut;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.game.yut.YutState.Phase;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 실제 시작 흐름(시작 카드 → 첫 차례)을 그대로 밟아서 '한 번 더' + 모 를 확인한다. */
class YutOpeningOneMoreFlowTests {

    final YutGame game = new YutGame();
    final Instant t0 = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void openingOneMoreThenMoGivesTwoMoreThrowsThroughTheRealFlow() {
        int checked = 0;
        StringBuilder log = new StringBuilder();
        for (long i = 1; i < 3000 && checked < 3; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            var s = (YutState) game.start(seed, Map.of("pieces", 3, "cards", true), List.of("a", "b"), t0);
            int idx = s.cardDraw.pile.indexOf(Card.ONE_MORE);
            if (idx < 0) continue;
            game.apply(s, "a", Map.of("type", "card", "index", idx), t0);
            // b 는 아무 카드나. b 의 카드가 a 의 던지기에 영향을 주면 안 된다
            game.apply(s, "b", Map.of("type", "card", "index", 0), t0);
            if (s.currentPlayer().equals("b")) continue; // b 가 휴식 등을 뽑아 순서가 바뀐 경우는 건너뜀
            assertThat(s.phase).isEqualTo(Phase.THROW);
            assertThat(s.effects("a").extraThrows).as("seed %d", seed).isEqualTo(1);

            game.apply(s, "a", Map.of("type", "throw"), t0);
            if (!"MO".equals(String.valueOf(s.lastEvent.get("result")))) continue;
            log.append("seed ").append(seed).append(" after MO: phase=").append(s.phase).append(" bonus=").append(s.bonusThrows).append(" free=").append(s.freeThrow).append('\n');
            assertThat(s.phase).isEqualTo(Phase.THROW);
            assertThat(s.bonusThrows).isEqualTo(1);

            game.apply(s, "a", Map.of("type", "throw"), t0);
            var second = String.valueOf(s.lastEvent.get("result"));
            if (second.equals("MO") || second.equals("YUT")) continue;
            int guard = 0;
            while (s.phase == Phase.MOVE && guard++ < 10) {
                var m = YutRules.legalMoves(s).get(0);
                var action = new HashMap<String, Object>();
                action.put("type", "move");
                action.put("pieceId", m.pieceId());
                action.put("result", m.result().name());
                action.put("steps", m.steps());
                if (m.via() != null) action.put("via", m.via());
                game.apply(s, "a", action, t0);
                if (s.phase == Phase.CARD) game.apply(s, "a", Map.of("type", "card", "index", 0), t0);
            }
            log.append("  after moves: player=").append(s.currentPlayer()).append(" phase=").append(s.phase).append(" bonus=").append(s.bonusThrows).append('\n');
            assertThat(s.currentPlayer()).as(log.toString()).isEqualTo("a");
            assertThat(s.phase).as(log.toString()).isEqualTo(Phase.THROW);
            checked++;
        }
        assertThat(checked).as(log.toString()).isGreaterThan(0);
    }
}
