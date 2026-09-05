package games.noriter.api.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 서버가 판을 갖고 판정하는 턴제 게임. 게임마다 구현체를 빈으로 등록한다. */
public interface TurnGame {

    String gameId();

    TurnState start(long seed, Map<String, Object> options, List<String> playerIds, Instant now);

    TurnState apply(TurnState state, String playerId, Map<String, Object> action, Instant now);

    /** 제한 시간 만료 등 자동 진행. 봇이 맡은 자리도 이걸로 움직인다. */
    TurnState auto(TurnState state, Instant now);

    TurnState leave(TurnState state, String playerId, Instant now);

    /** 이탈했던 참가자가 돌아옴. 봇이 맡던 자리를 돌려준다. */
    TurnState rejoin(TurnState state, String playerId, Instant now);
}
