package games.noriter.api.score;

/** 한 사용자의 게임별 기록. 턴제는 best 대신 wins(1등 횟수). */
public record UserGameStats(String gameId, String gameName, boolean turnBased, long plays, Long best, long wins) {}
