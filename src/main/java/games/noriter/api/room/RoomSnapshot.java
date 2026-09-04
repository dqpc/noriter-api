package games.noriter.api.room;

import java.time.Instant;
import java.util.List;

public record RoomSnapshot(
        String id,
        String gameId,
        RoomStatus status,
        String hostId,
        int maxPlayers,
        long seed,
        Instant startAt,
        Instant endAt,
        List<PlayerSnapshot> players) {

    public record PlayerSnapshot(String id, String nickname, long score, boolean finished, Integer rank) {}
}
