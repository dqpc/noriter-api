package games.noriter.api.room;

import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class RoomWebSocketHandler extends TextWebSocketHandler {

    private final RoomService rooms;
    private final RoomSessions sessions;
    private final ObjectMapper json;

    RoomWebSocketHandler(RoomService rooms, RoomSessions sessions, ObjectMapper json) {
        this.rooms = rooms;
        this.sessions = sessions;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        var roomId = roomId(session);
        if (rooms.find(roomId).isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("room not found"));
            return;
        }
        sessions.add(roomId, session);
        sessions.send(session, Map.of("type", "hello", "playerId", session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var roomId = roomId(session);
        var playerId = session.getId();
        try {
            JsonNode msg = json.readTree(message.getPayload());
            switch (msg.path("type").asText()) {
                case "join" -> rooms.join(roomId, playerId, msg.path("nickname").asText("player"));
                case "settings" -> rooms.setMaxPlayers(roomId, playerId, msg.path("maxPlayers").asInt());
                case "start" -> rooms.start(roomId, playerId);
                case "score" -> rooms.score(roomId, playerId, msg.path("score").asLong());
                case "finish" -> rooms.finish(roomId, playerId, msg.path("score").asLong());
                default -> sessions.send(session, Map.of("type", "error", "message", "unknown message type"));
            }
        } catch (RoomException e) {
            sessions.send(session, Map.of("type", "error", "message", e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var roomId = roomId(session);
        sessions.remove(roomId, session);
        rooms.leave(roomId, session.getId());
    }

    private static String roomId(WebSocketSession session) {
        var path = session.getUri() == null ? "" : session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
