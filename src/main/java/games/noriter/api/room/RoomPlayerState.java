package games.noriter.api.room;

import java.util.Map;

public record RoomPlayerState(String roomId, String playerId, Map<String, Object> state) {}
