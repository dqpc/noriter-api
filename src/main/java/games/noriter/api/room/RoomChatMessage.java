package games.noriter.api.room;

import java.time.Instant;

public record RoomChatMessage(String roomId, String playerId, String nickname, String text, boolean system, Instant sentAt) {

    public static final int MAX_LENGTH = 200;
}
