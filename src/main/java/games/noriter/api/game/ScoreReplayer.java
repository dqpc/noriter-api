package games.noriter.api.game;

import java.util.Map;

/**
 * 규칙이 클라이언트에 있는 게임의 점수 검증. 클라이언트가 보낸 입력 로그를 방의 seed 로 다시 돌려 점수를 계산한다.
 * 게임마다 구현체를 빈으로 등록한다.
 */
public interface ScoreReplayer {

    String gameId();

    /** moves 는 게임이 정한 한 글자 코드의 나열. 잘못된 수가 나오면 거기서 멈추고 그때까지의 점수를 돌려준다. */
    Replay replay(long seed, Map<String, Object> options, String moves);

    /** applied: 적용된 수의 개수, complete: 로그 전체가 끝까지 유효했는지 */
    record Replay(long score, int applied, boolean complete) {}
}
