package games.noriter.api.dm.web.dto;

import games.noriter.api.dm.MessageView;
import java.time.Instant;

public record MessageResponse(Long id, Long conversationId, Long senderId, String body, Instant createdAt) {

    public static MessageResponse from(MessageView m) {
        return new MessageResponse(m.id(), m.conversationId(), m.senderId(), m.body(), m.createdAt());
    }
}
