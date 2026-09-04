package games.noriter.api.room;

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
class RoomSessions implements RoomBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RoomSessions.class);

    private final ObjectMapper json;
    private final Map<String, Set<WebSocketSession>> byRoom = new ConcurrentHashMap<>();

    RoomSessions(ObjectMapper json) {
        this.json = json;
    }

    void add(String roomId, WebSocketSession session) {
        byRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    void remove(String roomId, WebSocketSession session) {
        var set = byRoom.get(roomId);
        if (set == null) return;
        set.remove(session);
        if (set.isEmpty()) byRoom.remove(roomId);
    }

    @Override
    public void broadcast(RoomSnapshot snapshot) {
        var set = byRoom.get(snapshot.id());
        if (set == null) return;
        var payload = Map.of("type", "room", "room", snapshot);
        set.forEach(s -> send(s, payload));
    }

    void send(WebSocketSession session, Object payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
            }
        } catch (IOException e) {
            log.warn("send failed to {}: {}", session.getId(), e.getMessage());
        }
    }
}
