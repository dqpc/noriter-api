package games.noriter.api.room.domain;

import games.noriter.api.room.RoomSnapshot;

public interface RoomBroadcaster {
    void broadcast(RoomSnapshot snapshot);
}
