package games.noriter.api.room;

import games.noriter.api.game.GameCatalog;
import games.noriter.api.room.domain.Room;
import games.noriter.api.room.domain.RoomBroadcaster;
import games.noriter.api.room.domain.RoomRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    static final Duration COUNTDOWN = Duration.ofSeconds(3);
    private static final String ID_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int ID_LENGTH = 8;

    private final RoomRepository rooms;
    private final GameCatalog games;
    private final List<RoomBroadcaster> broadcasters;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository rooms, GameCatalog games, List<RoomBroadcaster> broadcasters,
                TaskScheduler scheduler, Clock clock) {
        this.rooms = rooms;
        this.games = games;
        this.broadcasters = broadcasters;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public RoomSnapshot create(String gameId) {
        var room = new Room(newId(), games.require(gameId));
        rooms.save(room);
        return room.snapshot();
    }

    public Optional<RoomSnapshot> find(String roomId) {
        return rooms.find(roomId).map(Room::snapshot);
    }

    public RoomSnapshot join(String roomId, String playerId, String nickname) {
        var room = rooms.require(roomId);
        room.join(playerId, nickname);
        return publish(room);
    }

    public void leave(String roomId, String playerId) {
        rooms.find(roomId).ifPresent(room -> {
            room.leave(playerId);
            if (room.isEmpty()) {
                rooms.remove(roomId);
            } else {
                publish(room);
            }
        });
    }

    public RoomSnapshot setMaxPlayers(String roomId, String playerId, int maxPlayers) {
        var room = rooms.require(roomId);
        room.setMaxPlayers(playerId, maxPlayers);
        return publish(room);
    }

    public RoomSnapshot setOptions(String roomId, String playerId, Map<String, Object> options) {
        var room = rooms.require(roomId);
        room.setOptions(playerId, options);
        return publish(room);
    }

    public RoomSnapshot start(String roomId, String playerId) {
        var room = rooms.require(roomId);
        var startAt = Instant.now(clock).plus(COUNTDOWN);
        room.countdown(playerId, startAt, room.spec().seeded() ? (random.nextInt(Integer.MAX_VALUE - 1) + 1) : 0L);
        scheduler.schedule(() -> {
            if (room.play()) publish(room);
        }, startAt);
        if (room.endAt() != null) {
            scheduler.schedule(() -> {
                if (room.timeUp()) publish(room);
            }, room.endAt());
        }
        return publish(room);
    }

    public RoomSnapshot score(String roomId, String playerId, long score) {
        var room = rooms.require(roomId);
        room.score(playerId, score);
        return publish(room);
    }

    public RoomSnapshot finish(String roomId, String playerId, long score) {
        var room = rooms.require(roomId);
        room.finish(playerId, score);
        return publish(room);
    }

    private RoomSnapshot publish(Room room) {
        var snapshot = room.snapshot();
        broadcasters.forEach(b -> b.broadcast(snapshot));
        return snapshot;
    }

    private String newId() {
        var sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) sb.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        var id = sb.toString();
        return rooms.find(id).isPresent() ? newId() : id;
    }
}
