package games.noriter.api.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 협동처럼 서버가 상태를 갖는 게임의 규칙. 게임마다 하나씩 구현해 GameCatalog 에 등록한다. */
public interface SharedGame {

    String gameId();

    int players();

    SharedState start(long seed, Map<String, Object> options, List<String> playerIds, Instant now);

    SharedState apply(SharedState state, String playerId, Map<String, Object> input, Instant now);

    SharedState tick(SharedState state, Instant now);
}
