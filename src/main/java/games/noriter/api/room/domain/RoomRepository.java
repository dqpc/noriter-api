package games.noriter.api.room.domain;

import games.noriter.api.room.RoomException;
import java.util.Optional;

public interface RoomRepository {

    void save(Room room);

    Optional<Room> find(String id);

    void remove(String id);

    default Room require(String id) {
        return find(id).orElseThrow(() -> new RoomException("room not found: " + id));
    }
}
