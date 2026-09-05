package games.noriter.api.room.web;

import games.noriter.api.room.RoomException;
import games.noriter.api.room.RoomService;
import games.noriter.api.room.infra.RoomSessions;
import games.noriter.api.room.web.dto.ChatMessage;
import games.noriter.api.room.web.dto.ClientMessage;
import games.noriter.api.room.web.dto.ServerMessage;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
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
        sessions.send(session, new ServerMessage.Hello(session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var roomId = roomId(session);
        var playerId = session.getId();
        try {
            switch (json.readValue(message.getPayload(), ClientMessage.class)) {
                case ClientMessage.Join m -> {
                    sessions.send(session, new ServerMessage.ChatHistory(
                            rooms.chatHistory(roomId).stream().map(ChatMessage::from).toList()));
                    rooms.join(roomId, playerId, m.nickname() == null ? "player" : m.nickname());
                }
                case ClientMessage.Chat m -> rooms.chat(roomId, playerId, m.text());
                case ClientMessage.Settings m -> {
                    if (m.maxPlayers() != null) rooms.setMaxPlayers(roomId, playerId, m.maxPlayers());
                    if (m.options() != null) rooms.setOptions(roomId, playerId, m.options());
                }
                case ClientMessage.Start m -> rooms.start(roomId, playerId);
                case ClientMessage.Score m -> rooms.score(roomId, playerId, m.score());
                case ClientMessage.Finish m -> rooms.finish(roomId, playerId, m.score());
            }
        } catch (RoomException e) {
            sessions.send(session, new ServerMessage.Error(e.getMessage()));
        } catch (RuntimeException e) {
            sessions.send(session, new ServerMessage.Error("invalid message"));
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
