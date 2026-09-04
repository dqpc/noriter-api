package games.noriter.api.room.web.dto;

public sealed interface ServerMessage {

    String type();

    record Hello(String type, String playerId) implements ServerMessage {
        public Hello(String playerId) { this("hello", playerId); }
    }

    record RoomUpdate(String type, RoomResponse room) implements ServerMessage {
        public RoomUpdate(RoomResponse room) { this("room", room); }
    }

    record Error(String type, String message) implements ServerMessage {
        public Error(String message) { this("error", message); }
    }
}
