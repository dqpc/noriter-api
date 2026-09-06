package games.noriter.api.word.web.dto;

import games.noriter.api.word.WordStats;
import java.util.List;

public record StatsResponse(int played, int won, int winRate, int currentStreak, int maxStreak, List<Integer> distribution) {
    public static StatsResponse from(WordStats s) {
        return new StatsResponse(s.played(), s.won(), s.winRate(), s.currentStreak(), s.maxStreak(), s.distribution());
    }
}
