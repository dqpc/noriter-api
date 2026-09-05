package games.noriter.api.room.domain;

import games.noriter.api.room.RoomChatMessage;
import games.noriter.api.room.RoomException;
import games.noriter.api.room.RoomSnapshot;
import games.noriter.api.room.RoomStatus;

import games.noriter.api.game.GameSpec;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Room {

    private final String id;
    private final GameSpec spec;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Deque<RoomChatMessage> chat = new ArrayDeque<>();
    private static final int CHAT_HISTORY = 50;
    private RoomStatus status = RoomStatus.WAITING;
    private String hostId;
    private int maxPlayers;
    private final Map<String, Object> options = new LinkedHashMap<>();
    private long seed;
    private Instant startAt;
    private Instant endAt;

    public Room(String id, GameSpec spec) {
        this.id = id;
        this.spec = spec;
        this.maxPlayers = spec.defaultMaxPlayers();
        this.options.putAll(spec.defaultOptions());
    }

    public String id() { return id; }
    public GameSpec spec() { return spec; }
    public RoomStatus status() { return status; }
    public Instant startAt() { return startAt; }
    public Instant endAt() { return endAt; }
    public boolean isEmpty() { return players.isEmpty(); }

    public synchronized String nicknameOf(String playerId) {
        var p = players.get(playerId);
        return p == null ? null : p.nickname;
    }

    public synchronized boolean hasPlayer(String playerId) {
        return players.containsKey(playerId);
    }

    public synchronized void addChat(RoomChatMessage message) {
        chat.addLast(message);
        while (chat.size() > CHAT_HISTORY) chat.removeFirst();
    }

    public synchronized List<RoomChatMessage> chatHistory() {
        return List.copyOf(chat);
    }

    public synchronized void join(String playerId, String nickname) {
        if (players.containsKey(playerId)) return;
        if (status != RoomStatus.WAITING) throw new RoomException("game already started");
        if (players.size() >= maxPlayers) throw new RoomException("room is full");
        players.put(playerId, new Player(playerId, nickname));
        if (hostId == null) hostId = playerId;
    }

    public synchronized void leave(String playerId) {
        if (players.remove(playerId) == null) return;
        if (playerId.equals(hostId)) {
            hostId = players.isEmpty() ? null : players.keySet().iterator().next();
        }
        if (status == RoomStatus.PLAYING && allFinished()) status = RoomStatus.FINISHED;
    }

    public synchronized void setMaxPlayers(String playerId, int value) {
        requireHost(playerId);
        if (status != RoomStatus.WAITING) throw new RoomException("game already started");
        if (value < spec.minPlayers() || value > spec.maxPlayersLimit()) {
            throw new RoomException("maxPlayers must be between " + spec.minPlayers() + " and " + spec.maxPlayersLimit());
        }
        if (value < players.size()) throw new RoomException("more players than maxPlayers");
        maxPlayers = value;
    }

    public synchronized void setOptions(String playerId, Map<String, Object> changes) {
        requireHost(playerId);
        if (status != RoomStatus.WAITING) throw new RoomException("game already started");
        changes.forEach((k, v) -> {
            if (!spec.isAllowedOption(k, v)) throw new RoomException("invalid option " + k + "=" + v);
        });
        changes.forEach((k, v) -> options.put(k, normalize(k, v)));
    }

    private Object normalize(String key, Object value) {
        return spec.optionChoices().get(key).stream()
                .filter(c -> String.valueOf(c).equals(String.valueOf(value)))
                .findFirst().orElse(value);
    }

    public synchronized void countdown(String playerId, Instant startAt, long seed) {
        requireHost(playerId);
        if (status != RoomStatus.WAITING) throw new RoomException("game already started");
        if (players.size() < spec.minPlayers()) throw new RoomException("not enough players");
        this.status = RoomStatus.COUNTDOWN;
        this.startAt = startAt;
        this.endAt = spec.matchDuration() == null ? null : startAt.plus(spec.matchDuration());
        this.seed = seed;
    }

    public synchronized boolean play() {
        if (status != RoomStatus.COUNTDOWN) return false;
        status = RoomStatus.PLAYING;
        return true;
    }

    public synchronized void score(String playerId, long score) {
        if (status != RoomStatus.PLAYING) throw new RoomException("game is not running");
        var p = requirePlayer(playerId);
        if (p.finished) return;
        p.score = spec.higherIsBetter() ? Math.max(p.score, score) : score;
    }

    public synchronized void finish(String playerId, long score) {
        if (status != RoomStatus.PLAYING) throw new RoomException("game is not running");
        var p = requirePlayer(playerId);
        p.score = score;
        p.finished = true;
        if (allFinished()) status = RoomStatus.FINISHED;
    }

    public synchronized boolean timeUp() {
        if (status != RoomStatus.PLAYING) return false;
        players.values().forEach(p -> p.finished = true);
        status = RoomStatus.FINISHED;
        return true;
    }

    public synchronized RoomSnapshot snapshot() {
        var ranked = new ArrayList<>(players.values());
        Comparator<Player> byScore = Comparator.comparingLong(p -> p.score);
        ranked.sort(spec.higherIsBetter() ? byScore.reversed() : byScore);
        Map<String, Integer> ranks = new LinkedHashMap<>();
        if (status == RoomStatus.FINISHED) {
            int rank = 0;
            long prev = Long.MIN_VALUE;
            for (int i = 0; i < ranked.size(); i++) {
                var p = ranked.get(i);
                if (i == 0 || p.score != prev) rank = i + 1;
                ranks.put(p.id, rank);
                prev = p.score;
            }
        }
        List<RoomSnapshot.PlayerSnapshot> list = players.values().stream()
                .map(p -> new RoomSnapshot.PlayerSnapshot(p.id, p.nickname, p.score, p.finished, ranks.get(p.id)))
                .toList();
        var info = new RoomSnapshot.GameInfo(spec.name(), spec.minPlayers(), spec.maxPlayersLimit(),
                spec.matchDuration() == null ? null : spec.matchDuration().toSeconds(), spec.optionChoices());
        return new RoomSnapshot(id, spec.id(), info, status, hostId, maxPlayers, Map.copyOf(options), seed, startAt, endAt, list);
    }

    private boolean allFinished() {
        return !players.isEmpty() && players.values().stream().allMatch(p -> p.finished);
    }

    private void requireHost(String playerId) {
        if (!playerId.equals(hostId)) throw new RoomException("only host can do this");
    }

    private Player requirePlayer(String playerId) {
        var p = players.get(playerId);
        if (p == null) throw new RoomException("not in room");
        return p;
    }

    private static final class Player {
        final String id;
        final String nickname;
        long score;
        boolean finished;

        Player(String id, String nickname) {
            this.id = id;
            this.nickname = nickname;
        }
    }
}
