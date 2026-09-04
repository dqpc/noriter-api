package games.noriter.api.room.infra;

import games.noriter.api.room.domain.Room;
import games.noriter.api.room.domain.RoomRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryRoomRepository implements RoomRepository {

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public void save(Room room) {
        rooms.put(room.id(), room);
    }

    @Override
    public Optional<Room> find(String id) {
        return Optional.ofNullable(rooms.get(id));
    }

    @Override
    public void remove(String id) {
        rooms.remove(id);
    }
}
