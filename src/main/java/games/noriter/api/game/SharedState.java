package games.noriter.api.game;

import java.time.Instant;
import java.util.Map;

public interface SharedState {

    boolean ended();

    long score();

    /** 서버가 다음에 상태를 재평가해야 하는 시각. 없으면 null */
    Instant deadline();

    /** 클라이언트에 보내는 표현 */
    Map<String, Object> view();
}
