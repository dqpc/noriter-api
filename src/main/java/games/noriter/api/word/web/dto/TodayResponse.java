package games.noriter.api.word.web.dto;

import games.noriter.api.word.WordToday;
import java.time.Instant;
import java.time.LocalDate;

public record TodayResponse(int number, LocalDate date, int tries, int length, Instant resetAt) {
    public static TodayResponse from(WordToday t) {
        return new TodayResponse(t.number(), t.date(), t.tries(), t.length(), t.resetAt());
    }
}
