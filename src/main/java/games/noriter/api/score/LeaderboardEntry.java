package games.noriter.api.score;

public record LeaderboardEntry(int rank, Long userId, String nickname, long score) {}
