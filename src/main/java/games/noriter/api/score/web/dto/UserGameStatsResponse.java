package games.noriter.api.score.web.dto;

import games.noriter.api.score.UserGameStats;

public record UserGameStatsResponse(String gameId, String gameName, boolean turnBased, long plays, Long best, long wins) {

    public static UserGameStatsResponse from(UserGameStats s) {
        return new UserGameStatsResponse(s.gameId(), s.gameName(), s.turnBased(), s.plays(), s.best(), s.wins());
    }
}
