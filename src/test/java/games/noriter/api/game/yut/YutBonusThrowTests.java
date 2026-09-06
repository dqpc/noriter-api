package games.noriter.api.game.yut;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.game.yut.YutState.Phase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 시작 카드 '한 번 더' 를 받은 사람이 첫 던지기에서 모를 던지면, 모의 추가 던지기와 카드의 추가 던지기가 둘 다 살아 있어야 한다. */
class YutBonusThrowTests {

    final YutGame game = new YutGame();
    final Instant t0 = Instant.parse("2026-09-06T00:00:00Z");

    YutState stateWithOpeningOneMore(long seed) {
        var s = new YutState(seed, List.of("a", "b"), true, true, 3, YutGame.TURN_SECONDS, YutGame.CARD_SECONDS, YutGame.BOT_DELAY_SECONDS);
        s.effects("a").turnNo = 1;
        s.effects("a").extraThrows = 1;
        s.phase = Phase.THROW;
        s.deadline = t0.plusSeconds(YutGame.TURN_SECONDS);
        return s;
    }

    String throwOnce(YutState s) {
        game.apply(s, "a", Map.of("type", "throw"), t0);
        return String.valueOf(s.lastEvent.get("result"));
    }

    void moveEverything(YutState s) {
        int guard = 0;
        while (s.phase == Phase.MOVE && guard++ < 10) {
            var m = YutRules.legalMoves(s).get(0);
            var action = new java.util.HashMap<String, Object>();
            action.put("type", "move");
            action.put("pieceId", m.pieceId());
            action.put("result", m.result().name());
            action.put("steps", m.steps());
            if (m.via() != null) action.put("via", m.via());
            game.apply(s, "a", action, t0);
        }
    }

    @Test
    void moAfterOpeningOneMoreLeavesTwoMoreThrows() {
        int checked = 0;
        for (long i = 1; i < 400 && checked < 5; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            var s = stateWithOpeningOneMore(seed);
            if (!throwOnce(s).equals("MO")) continue;
            assertThat(s.phase).as("seed %d: 모 뒤 다시 던지기", seed).isEqualTo(Phase.THROW);
            assertThat(s.bonusThrows).as("seed %d: 카드의 추가 던지기 보존", seed).isEqualTo(1);

            var second = throwOnce(s);
            if (second.equals("MO") || second.equals("YUT")) continue;
            assertThat(s.phase).isEqualTo(Phase.MOVE);
            moveEverything(s);
            assertThat(s.currentPlayer()).as("seed %d: 아직 a 의 차례", seed).isEqualTo("a");
            assertThat(s.phase).as("seed %d: 카드 덕분에 한 번 더", seed).isEqualTo(Phase.THROW);
            checked++;
        }
        assertThat(checked).isGreaterThan(0);
    }
}
