package games.noriter.api.score;

import org.springframework.modulith.events.Externalized;

public record ScoreSubmitted(String gameId, Long userId, long score) {}
