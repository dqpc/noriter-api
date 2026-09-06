package games.noriter.api.dm.web.dto;

import games.noriter.api.dm.ConversationView;
import java.time.Instant;

public record ConversationResponse(Long id, Long otherUserId, String otherNickname, String otherCharacterId,
                                   MessageResponse lastMessage, long unread, Instant lastMessageAt) {

    public static ConversationResponse from(ConversationView v) {
        return new ConversationResponse(v.id(), v.otherUserId(), v.otherNickname(), v.otherCharacterId(),
                v.lastMessage() == null ? null : MessageResponse.from(v.lastMessage()), v.unread(), v.lastMessageAt());
    }
}
