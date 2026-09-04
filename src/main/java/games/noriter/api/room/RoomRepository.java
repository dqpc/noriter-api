package games.noriter.api.room;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
class RoomRepository {

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

    void save(Room room) {
        rooms.put(room.id(), room);
    }

    Optional<Room> find(String id) {
        return Optional.ofNullable(rooms.get(id));
    }

    Room require(String id) {
        return find(id).orElseThrow(() -> new RoomException("room not found: " + id));
    }

    void remove(String id) {
        rooms.remove(id);
    }
}
