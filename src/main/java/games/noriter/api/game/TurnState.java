package games.noriter.api.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface TurnState {

    boolean ended();

    /** 순위순 playerId (1등부터). 끝나기 전에는 현재 순위 추정. */
    List<String> ranking();

    Map<String, Long> scores();

    /** 다음에 서버가 auto() 를 불러야 하는 시각. 없으면 null */
    Instant deadline();

    /** 현재 차례. 봇 차례면 봇이 바로 진행하도록 room 이 auto() 를 부른다. */
    String currentPlayer();

    boolean isBot(String playerId);

    Map<String, Object> view();
}
