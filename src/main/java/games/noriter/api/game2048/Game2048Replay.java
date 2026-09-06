package games.noriter.api.game2048;

import games.noriter.api.game.ScoreReplayer;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 2048 입력 로그를 방의 seed·목표 타일로 재생해 점수를 다시 계산한다 */
@Component
public class Game2048Replay implements ScoreReplayer {

    static final String GAME_ID = "2048";
    /** 판 하나가 이보다 길 수는 없다. 그 이상은 조작으로 보고 자른다 */
    static final int MAX_MOVES = 10_000;

    @Override
    public String gameId() {
        return GAME_ID;
    }

    @Override
    public Replay replay(long seed, Map<String, Object> options, String moves) {
        var board = new Board2048(seed, target(options));
        var log = moves == null ? "" : moves;
        int limit = Math.min(log.length(), MAX_MOVES);
        int applied = 0;
        while (applied < limit && board.step(log.charAt(applied))) applied++;
        return new Replay(board.score(), applied, applied == log.length());
    }

    private static int target(Map<String, Object> options) {
        var raw = options == null ? null : options.get("target");
        if (raw == null) return Board2048.DEFAULT_TARGET;
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return Board2048.DEFAULT_TARGET;
        }
    }
}
