package games.noriter.api.room;

import java.util.Map;

public record RoomGameState(String roomId, Map<String, Object> view) {}
