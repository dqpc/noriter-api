package games.noriter.api.score;

public record BestScoreUpdated(Long userId, String gameId, String gameName, long score, long previousBest) {}
