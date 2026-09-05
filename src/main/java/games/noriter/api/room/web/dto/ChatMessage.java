package games.noriter.api.room.web.dto;

import games.noriter.api.room.RoomChatMessage;
import java.time.Instant;

public record ChatMessage(String playerId, String nickname, String text, boolean system, Instant sentAt) {

    public static ChatMessage from(RoomChatMessage m) {
        return new ChatMessage(m.playerId(), m.nickname(), m.text(), m.system(), m.sentAt());
    }
}
