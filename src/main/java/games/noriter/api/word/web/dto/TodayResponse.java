package games.noriter.api.word.web.dto;

import games.noriter.api.word.WordGuessView;
import games.noriter.api.word.WordJudge;
import games.noriter.api.word.WordToday;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** guesses 는 계정 사용자가 오늘 이미 보낸 추측(복원용). 게스트는 null */
public record TodayResponse(int number, LocalDate date, int tries, int length, Instant resetAt, List<Guess> guesses) {

    public record Guess(String jamo, List<String> statuses) {
        static Guess from(WordGuessView v) {
            return new Guess(v.jamo(), v.statuses().stream().map(WordJudge.Status::json).toList());
        }
    }

    public static TodayResponse from(WordToday t, List<WordGuessView> guesses) {
        return new TodayResponse(t.number(), t.date(), t.tries(), t.length(), t.resetAt(),
                guesses == null ? null : guesses.stream().map(Guess::from).toList());
    }
}
