package games.noriter.api.room;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RoomSnapshot(
        String id,
        String gameId,
        RoomStatus status,
        String hostId,
        int maxPlayers,
        Map<String, Object> options,
        long seed,
        Instant startAt,
        Instant endAt,
        List<PlayerSnapshot> players) {

    public record PlayerSnapshot(String id, String nickname, long score, boolean finished, Integer rank) {}
}
