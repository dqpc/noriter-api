package games.noriter.api.room;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RoomSnapshot(
        String id,
        String gameId,
        GameInfo game,
        RoomStatus status,
        String hostId,
        int maxPlayers,
        Map<String, Object> options,
        long seed,
        Instant startAt,
        Instant endAt,
        List<PlayerSnapshot> players) {

    public record PlayerSnapshot(String id, String nickname, String character, long score, boolean finished, Integer rank) {}

    public record GameInfo(
            String name,
            int minPlayers,
            int maxPlayersLimit,
            Long matchDurationSeconds,
            Map<String, List<Object>> optionChoices,
            boolean turnBased,
            boolean uniqueCharacters) {}
}
