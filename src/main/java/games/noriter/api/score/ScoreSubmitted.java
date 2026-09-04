package games.noriter.api.score;

import org.springframework.modulith.events.Externalized;

/** 점수가 저장되면 발행되는 도메인 이벤트. 다른 모듈은 이 이벤트로만 반응한다. */
public record ScoreSubmitted(String gameId, Long userId, long score) {}
