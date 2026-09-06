package games.noriter.api.room;

import java.util.List;

/** 한 판이 끝나 순위가 확정됐을 때. 로그인한 참가자(userId 있음)의 기록·알림은 이 이벤트로 다른 모듈이 처리한다. */
public record RoomFinished(String roomId, String gameId, String gameName, boolean turnBased, boolean higherIsBetter, List<Result> results) {

    public record Result(String playerId, Long userId, String nickname, long score, Integer rank) {}
}
