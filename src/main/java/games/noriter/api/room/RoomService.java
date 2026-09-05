package games.noriter.api.room;

import games.noriter.api.game.GameCatalog;
import games.noriter.api.game.TurnGame;
import games.noriter.api.game.TurnState;
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
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomService {

    static final Duration COUNTDOWN = Duration.ofSeconds(3);
    static final Duration ABANDON_GRACE = Duration.ofSeconds(60);
    private static final String ID_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int ID_LENGTH = 8;

    private final RoomRepository rooms;
    private final GameCatalog games;
    private final List<RoomBroadcaster> broadcasters;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RoomSnapshot create(String gameId) {
        var room = new Room(newId(), games.require(gameId));
        rooms.save(room);
        return room.snapshot();
    }

    public Optional<RoomSnapshot> find(String roomId) {
        return rooms.find(roomId).map(Room::snapshot);
    }

    public RoomSnapshot join(String roomId, String playerId, String nickname, String character) {
        var room = rooms.require(roomId);
        var joined = room.join(playerId, nickname, character);
        var name = room.nicknameOf(playerId);
        switch (joined) {
            case NEW -> system(room, name + " 님이 들어왔습니다");
            case REJOINED -> {
                if (room.status() == RoomStatus.PLAYING && room.turn() != null) {
                    var engine = games.turnGame(room.spec().id()).orElseThrow();
                    settle(room, engine, engine.rejoin(room.turn(), playerId, Instant.now(clock)));
                }
                system(room, name + " 님이 다시 들어왔습니다");
            }
            case ALREADY -> { }
        }
        var snap = publish(room);
        if (room.turn() != null) broadcasters.forEach(b -> b.gameState(new RoomGameState(room.id(), room.turn().view())));
        return snap;
    }

    public void leave(String roomId, String playerId) {
        rooms.find(roomId).ifPresent(room -> {
            var nickname = room.nicknameOf(playerId);
            if (nickname == null) return;
            boolean seatKept = room.disconnect(playerId);
            if (seatKept) {
                if (room.spec().turnBased() && room.status() == RoomStatus.PLAYING && room.turn() != null) {
                    var engine = games.turnGame(room.spec().id()).orElseThrow();
                    settle(room, engine, engine.leave(room.turn(), playerId, Instant.now(clock)));
                    system(room, nickname + " 님의 연결이 끊겼습니다. 돌아올 때까지 봇이 대신합니다");
                } else {
                    system(room, nickname + " 님의 연결이 끊겼습니다");
                }
                publish(room);
                if (!room.hasConnectedPlayer()) {
                    scheduler.schedule(() -> {
                        if (!room.hasConnectedPlayer()) rooms.remove(roomId);
                    }, Instant.now(clock).plus(ABANDON_GRACE));
                }
                return;
            }
            if (room.isEmpty() || !room.hasConnectedPlayer()) {
                rooms.remove(roomId);
            } else {
                system(room, nickname + " 님이 나갔습니다");
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

    public RoomSnapshot rematch(String roomId, String playerId) {
        var room = rooms.require(roomId);
        room.rematch(playerId);
        return beginCountdown(room, playerId);
    }

    public void relayState(String roomId, String playerId, Map<String, Object> state) {
        var room = rooms.require(roomId);
        if (room.status() != RoomStatus.PLAYING || !room.hasPlayer(playerId)) return;
        var msg = new RoomPlayerState(roomId, playerId, state);
        broadcasters.forEach(b -> b.playerState(msg));
    }

    public void action(String roomId, String playerId, Map<String, Object> action) {
        var room = rooms.require(roomId);
        if (!room.spec().turnBased() || room.status() != RoomStatus.PLAYING || room.turn() == null) throw new RoomException("game is not running");
        if (!room.hasPlayer(playerId)) throw new RoomException("not in room");
        var engine = games.turnGame(room.spec().id()).orElseThrow();
        try {
            settle(room, engine, engine.apply(room.turn(), playerId, action, Instant.now(clock)));
        } catch (IllegalArgumentException e) {
            throw new RoomException(e.getMessage());
        }
    }

    private void settle(Room room, TurnGame engine, TurnState state) {
        int version = room.setTurn(state);
        broadcasters.forEach(b -> b.gameState(new RoomGameState(room.id(), state.view())));
        if (state.ended()) {
            room.finishWithScores(state.scores());
            publish(room);
            return;
        }
        var deadline = state.deadline();
        if (deadline != null) {
            scheduler.schedule(() -> {
                if (room.status() != RoomStatus.PLAYING || room.turnVersion() != version) return;
                settle(room, engine, engine.auto(state, Instant.now(clock)));
            }, deadline);
        }
    }

    public RoomSnapshot setCharacter(String roomId, String playerId, String character) {
        var room = rooms.require(roomId);
        room.setCharacter(playerId, character);
        return publish(room);
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
        return beginCountdown(room, playerId);
    }

    private RoomSnapshot beginCountdown(Room room, String playerId) {
        var startAt = Instant.now(clock).plus(COUNTDOWN);
        long seed = room.spec().seeded() ? (random.nextInt(Integer.MAX_VALUE - 1) + 1) : 0L;
        room.countdown(playerId, startAt, seed);
        scheduler.schedule(() -> {
            if (room.play()) {
                if (room.spec().turnBased()) {
                    var engine = games.turnGame(room.spec().id()).orElseThrow();
                    var now = Instant.now(clock);
                    var state = engine.start(seed, room.snapshot().options(), room.playerIds(), now);
                    for (var gone : room.disconnectedPlayerIds()) state = engine.leave(state, gone, now);
                    settle(room, engine, state);
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
