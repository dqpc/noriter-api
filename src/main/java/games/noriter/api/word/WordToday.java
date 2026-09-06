package games.noriter.api.word;

import java.time.Instant;
import java.time.LocalDate;

public record WordToday(int number, LocalDate date, int tries, int length, Instant resetAt) {}
