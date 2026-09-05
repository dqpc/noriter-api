package games.noriter.api.room;

import games.noriter.api.game.GameCatalog;
import games.noriter.api.game.GameMode;
import games.noriter.api.game.SharedGame;
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

    public RoomSnapshot create(String gameId, GameMode mode) {
        var spec = games.require(gameId);
        if (!spec.supports(mode)) throw new RoomException("mode not supported: " + mode);
        int size = mode == GameMode.COOP
                ? games.sharedGame(gameId).orElseThrow(() -> new RoomException("coop not available: " + gameId)).players()
                : spec.defaultMaxPlayers();
        var room = new Room(newId(), spec, mode, size);
        rooms.save(room);
        return room.snapshot();
    }

    public RoomSnapshot create(String gameId) {
        return create(gameId, GameMode.VERSUS);
    }

    public Optional<RoomSnapshot> find(String roomId) {
        return rooms.find(roomId).map(Room::snapshot);
    }

    public RoomSnapshot join(String roomId, String playerId, String nickname) {
        var room = rooms.require(roomId);
        room.join(playerId, nickname);
        system(room, nickname + " 님이 들어왔습니다");
        return publish(room);
    }

    public void leave(String roomId, String playerId) {
        rooms.find(roomId).ifPresent(room -> {
            var nickname = room.nicknameOf(playerId);
            room.leave(playerId);
            if (room.isEmpty()) {
                rooms.remove(roomId);
            } else {
                if (nickname != null) system(room, nickname + " 님이 나갔습니다");
                publish(room);
            }
        });
    }

    public List<RoomChatMessage> chatHistory(String roomId) {
        return rooms.require(roomId).chatHistory();
    }

    public void chat(String roomId, String playerId, String text) {
        var room = rooms.require(roomId);
        var nickname = room.nicknameOf(playerId);
        if (nickname == null) throw new RoomException("not in room");
        var trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) return;
        if (trimmed.length() > RoomChatMessage.MAX_LENGTH) throw new RoomException("message too long");
        deliver(room, new RoomChatMessage(room.id(), playerId, nickname, trimmed, false, Instant.now(clock)));
    }

    private void system(Room room, String text) {
        deliver(room, new RoomChatMessage(room.id(), null, null, text, true, Instant.now(clock)));
    }

    private void deliver(Room room, RoomChatMessage message) {
        room.addChat(message);
        broadcasters.forEach(b -> b.chat(message));
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
        long seed = room.spec().seeded() ? (random.nextInt(Integer.MAX_VALUE - 1) + 1) : 0L;
        room.countdown(playerId, startAt, seed);
        scheduler.schedule(() -> {
            if (room.play()) {
                if (room.mode() == GameMode.COOP) {
                    var engine = games.sharedGame(room.spec().id()).orElseThrow();
                    room.shared(engine.start(seed, room.snapshot().options(), room.playerIds(), Instant.now(clock)));
                    publishGameState(room);
                }
                publish(room);
            }
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

    public void input(String roomId, String playerId, Map<String, Object> input) {
        var room = rooms.require(roomId);
        if (room.mode() != GameMode.COOP || room.status() != RoomStatus.PLAYING) throw new RoomException("game is not running");
        if (!room.hasPlayer(playerId)) throw new RoomException("not in room");
        var engine = games.sharedGame(room.spec().id()).orElseThrow();
        var next = engine.apply(room.shared(), playerId, input, Instant.now(clock));
        settle(room, engine, next);
    }

    private void settle(Room room, SharedGame engine, games.noriter.api.game.SharedState next) {
        room.shared(next);
        publishGameState(room);
        if (next.ended()) {
            room.finishAll(next.score());
            publish(room);
            return;
        }
        var deadline = next.deadline();
        if (deadline != null) {
            scheduler.schedule(() -> {
                if (room.status() != RoomStatus.PLAYING || room.shared() != next) return;
                settle(room, engine, engine.tick(next, Instant.now(clock)));
            }, deadline);
        }
    }

    private void publishGameState(Room room) {
        var state = new RoomGameState(room.id(), room.shared().view());
        broadcasters.forEach(b -> b.gameState(state));
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
