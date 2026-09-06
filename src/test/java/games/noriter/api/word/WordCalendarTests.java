package games.noriter.api.word;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WordCalendarTests {

    @Test
    void firstPuzzleIsSeptemberSixthKst() {
        assertThat(WordCalendar.numberOf(LocalDate.of(2026, 9, 6))).isEqualTo(1);
        assertThat(WordCalendar.numberOf(LocalDate.of(2026, 9, 8))).isEqualTo(3);
        assertThat(WordCalendar.dateOf(3)).isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void dayRollsOverAtKstMidnight() {
        // UTC 15:00 = KST 다음날 00:00
        var beforeMidnight = Clock.fixed(Instant.parse("2026-09-06T14:59:59Z"), ZoneOffset.UTC);
        var afterMidnight = Clock.fixed(Instant.parse("2026-09-06T15:00:00Z"), ZoneOffset.UTC);
        assertThat(WordCalendar.today(beforeMidnight)).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(WordCalendar.today(afterMidnight)).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(WordCalendar.resetAt(LocalDate.of(2026, 9, 6))).isEqualTo(Instant.parse("2026-09-06T15:00:00Z"));
    }
}
