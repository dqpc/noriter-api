package games.noriter.api.game;

import java.time.Duration;

public record GameSpec(
        String id,
        String name,
        int minPlayers,
        int defaultMaxPlayers,
        int maxPlayersLimit,
        Duration matchDuration,
        boolean seeded,
        boolean higherIsBetter) {

    public GameSpec {
        if (minPlayers < 1 || defaultMaxPlayers < minPlayers || maxPlayersLimit < defaultMaxPlayers) {
            throw new IllegalArgumentException("invalid player bounds for " + id);
        }
    }
}
