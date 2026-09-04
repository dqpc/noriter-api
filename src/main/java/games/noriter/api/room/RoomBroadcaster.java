package games.noriter.api.room;

interface RoomBroadcaster {
    void broadcast(RoomSnapshot snapshot);
}
