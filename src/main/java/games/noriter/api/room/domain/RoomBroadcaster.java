package games.noriter.api.room.domain;

import games.noriter.api.room.RoomChatMessage;
import games.noriter.api.room.RoomPlayerState;
import games.noriter.api.room.RoomSnapshot;

public interface RoomBroadcaster {
    void broadcast(RoomSnapshot snapshot);
    void chat(RoomChatMessage message);
    void playerState(RoomPlayerState state);
}
