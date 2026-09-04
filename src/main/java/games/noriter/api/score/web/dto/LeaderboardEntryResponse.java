package games.noriter.api.score.web.dto;

import games.noriter.api.score.LeaderboardEntry;

public record LeaderboardEntryResponse(int rank, Long userId, String nickname, long score) {

    public static LeaderboardEntryResponse from(LeaderboardEntry e) {
        return new LeaderboardEntryResponse(e.rank(), e.userId(), e.nickname(), e.score());
    }
}
