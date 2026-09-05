package games.noriter.api.room.web.dto;

import games.noriter.api.room.RoomSnapshot;
import games.noriter.api.room.RoomStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RoomResponse(
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
        List<Player> players) {

    public record GameInfo(String name, int minPlayers, int maxPlayersLimit, Long matchDurationSeconds,
                           Map<String, List<Object>> optionChoices, boolean turnBased, boolean uniqueCharacters) {}

    public record Player(String id, String nickname, String character, long score, boolean finished, Integer rank, boolean connected) {}

    public static RoomResponse from(RoomSnapshot s) {
        var g = s.game();
        return new RoomResponse(
                s.id(), s.gameId(),
                new GameInfo(g.name(), g.minPlayers(), g.maxPlayersLimit(), g.matchDurationSeconds(), g.optionChoices(), g.turnBased(), g.uniqueCharacters()),
                s.status(), s.hostId(), s.maxPlayers(), s.options(), s.seed(), s.startAt(), s.endAt(),
                s.players().stream().map(p -> new Player(p.id(), p.nickname(), p.character(), p.score(), p.finished(), p.rank(), p.connected())).toList());
    }
}
