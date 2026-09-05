package games.noriter.api.room.infra;

import games.noriter.api.room.RoomChatMessage;
import games.noriter.api.room.RoomGameState;
import games.noriter.api.room.RoomSnapshot;
import games.noriter.api.room.domain.RoomBroadcaster;
import games.noriter.api.room.web.dto.ChatMessage;
import games.noriter.api.room.web.dto.RoomResponse;
import games.noriter.api.room.web.dto.ServerMessage;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Component
public class RoomSessions implements RoomBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RoomSessions.class);

    private final ObjectMapper json;
    private final Map<String, Set<WebSocketSession>> byRoom = new ConcurrentHashMap<>();

    RoomSessions(ObjectMapper json) {
        this.json = json;
    }

    public void add(String roomId, WebSocketSession session) {
        byRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void remove(String roomId, WebSocketSession session) {
        var set = byRoom.get(roomId);
        if (set == null) return;
        set.remove(session);
        if (set.isEmpty()) byRoom.remove(roomId);
    }

    @Override
    public void broadcast(RoomSnapshot snapshot) {
        var set = byRoom.get(snapshot.id());
        if (set == null) return;
        var payload = new ServerMessage.RoomUpdate(RoomResponse.from(snapshot));
        set.forEach(s -> send(s, payload));
    }

    @Override
    public void chat(RoomChatMessage message) {
        var set = byRoom.get(message.roomId());
        if (set == null) return;
        var payload = new ServerMessage.Chat(ChatMessage.from(message));
        set.forEach(s -> send(s, payload));
    }

    @Override
    public void gameState(RoomGameState state) {
        var set = byRoom.get(state.roomId());
        if (set == null) return;
        var payload = new ServerMessage.GameState(state.view());
        set.forEach(s -> send(s, payload));
    }

    public void send(WebSocketSession session, ServerMessage payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
            }
        } catch (IOException e) {
            log.warn("send failed to {}: {}", session.getId(), e.getMessage());
        }
    }
}
