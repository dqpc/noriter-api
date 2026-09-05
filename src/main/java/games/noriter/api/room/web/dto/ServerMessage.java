package games.noriter.api.room.web.dto;

public sealed interface ServerMessage {

    String type();

    record Hello(String type, String playerId) implements ServerMessage {
        public Hello(String playerId) { this("hello", playerId); }
    }

    /** serverTime: 클라이언트가 자기 시계와의 차이를 보정해 카운트다운·제한 시간을 맞추는 기준 */
    record RoomUpdate(String type, RoomResponse room, java.time.Instant serverTime) implements ServerMessage {
        public RoomUpdate(RoomResponse room) { this("room", room, java.time.Instant.now()); }
    }

    record Error(String type, String message) implements ServerMessage {
        public Error(String message) { this("error", message); }
    }

    record GameState(String type, java.util.Map<String, Object> state, java.time.Instant serverTime) implements ServerMessage {
        public GameState(java.util.Map<String, Object> state) { this("gameState", state, java.time.Instant.now()); }
    }

    record PlayerState(String type, String playerId, java.util.Map<String, Object> state) implements ServerMessage {
        public PlayerState(String playerId, java.util.Map<String, Object> state) { this("playerState", playerId, state); }
    }

    record Pong(String type) implements ServerMessage {
        public Pong() { this("pong"); }
    }

    record Chat(String type, ChatMessage message) implements ServerMessage {
        public Chat(ChatMessage message) { this("chat", message); }
    }

    record ChatHistory(String type, java.util.List<ChatMessage> messages) implements ServerMessage {
        public ChatHistory(java.util.List<ChatMessage> messages) { this("chatHistory", messages); }
    }
}
