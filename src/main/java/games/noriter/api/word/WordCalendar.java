package games.noriter.api.word;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** 오늘의 문제 번호. KST 날짜 기준으로 2026-09-06 이 1번, 자정에 다음 번호로 넘어간다. */
public final class WordCalendar {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final LocalDate EPOCH = LocalDate.of(2026, 9, 6);

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static int numberOf(LocalDate date) {
        return (int) ChronoUnit.DAYS.between(EPOCH, date) + 1;
    }

    public static LocalDate dateOf(int number) {
        return EPOCH.plusDays(number - 1L);
    }

    public static Instant resetAt(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    private WordCalendar() {}
}
