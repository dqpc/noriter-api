package games.noriter.api.room.domain;

import games.noriter.api.room.RoomChatMessage;
import games.noriter.api.room.RoomException;
import games.noriter.api.room.RoomSnapshot;
import games.noriter.api.room.RoomStatus;

import games.noriter.api.game.GameSpec;
import games.noriter.api.game.TurnState;
import java.time.Duration;
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
    private TurnState turn;
    private int turnVersion;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Deque<RoomChatMessage> chat = new ArrayDeque<>();
    private static final int CHAT_HISTORY = 50;
    private RoomStatus status = RoomStatus.WAITING;
    private boolean resultReported;
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
    public synchronized boolean hasConnectedPlayer() { return players.values().stream().anyMatch(p -> p.connected); }

    public TurnState turn() { return turn; }
    public synchronized int turnVersion() { return turnVersion; }
    public synchronized int setTurn(TurnState state) { this.turn = state; return ++turnVersion; }

    public synchronized List<String> playerIds() {
        return List.copyOf(players.keySet());
    }

    public synchronized boolean hasDuplicateCharacters() {
        var seen = new java.util.HashSet<String>();
        for (var p : players.values()) if (p.character != null && !seen.add(p.character)) return true;
        return false;
    }

    public synchronized void finishWithScores(Map<String, Long> scores) {
        players.values().forEach(p -> { p.score = scores.getOrDefault(p.id, 0L); p.finished = true; });
        status = RoomStatus.FINISHED;
    }

    /** 종료된 판의 결과를 한 번만 내보내기 위한 표시. 처음 호출에만 true. */
    public synchronized boolean markResultReported() {
        if (status != RoomStatus.FINISHED || resultReported) return false;
        resultReported = true;
        return true;
    }

    public synchronized Long userIdOf(String playerId) {
        var p = players.get(playerId);
        return p == null ? null : p.userId;
    }

    public synchronized String playerIdOfUser(Long userId) {
        if (userId == null) return null;
        return players.values().stream().filter(p -> userId.equals(p.userId)).map(p -> p.id).findFirst().orElse(null);
    }

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

    public enum Joined { NEW, REJOINED, ALREADY }

    public synchronized Joined join(String playerId, String nickname, String character, Long userId) {
        var existing = players.get(playerId);
        if (existing != null) {
            var result = existing.connected ? Joined.ALREADY : Joined.REJOINED;
            existing.connected = true;
            return result;
        }
        if (status != RoomStatus.WAITING && status != RoomStatus.FINISHED) throw new RoomException("game already started");
        if (players.size() >= maxPlayers) throw new RoomException("room is full");
        players.put(playerId, new Player(playerId, nickname, character, userId));
        if (hostId == null) hostId = playerId;
        return Joined.NEW;
    }

    /** 대기·종료 중이면 방에서 빠지고, 진행 중이면 자리를 남긴 채 연결만 끊긴 것으로 둔다. @return true 면 자리 유지 */
    public synchronized boolean disconnect(String playerId) {
        var p = players.get(playerId);
        if (p == null) return false;
        if (status == RoomStatus.COUNTDOWN || status == RoomStatus.PLAYING) {
            p.connected = false;
            passHostIfNeeded(playerId);
            if (status == RoomStatus.PLAYING && allFinished()) status = RoomStatus.FINISHED;
            return true;
        }
        players.remove(playerId);
        passHostIfNeeded(playerId);
        return false;
    }

    public synchronized List<String> disconnectedPlayerIds() {
        return players.values().stream().filter(p -> !p.connected).map(p -> p.id).toList();
    }

    /** 방장이 다른 참가자에게 방장을 넘긴다. 대기·종료 중에만. */
    public synchronized void transferHost(String from, String to) {
        requireHost(from);
        if (status != RoomStatus.WAITING && status != RoomStatus.FINISHED) throw new RoomException("game is running");
        var target = requirePlayer(to);
        if (!target.connected) throw new RoomException("player is disconnected");
        hostId = to;
    }

    private void passHostIfNeeded(String playerId) {
        if (!playerId.equals(hostId)) return;
        hostId = players.values().stream().filter(p -> p.connected).map(p -> p.id).findFirst()
                .orElse(players.isEmpty() ? null : players.keySet().iterator().next());
    }

    public synchronized void setCharacter(String playerId, String character) {
        requirePlayer(playerId).character = character;
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
        if (spec.uniqueCharacters() && hasDuplicateCharacters()) throw new RoomException("duplicate characters");
        this.status = RoomStatus.COUNTDOWN;
        this.startAt = startAt;
        this.endAt = spec.matchDuration() == null ? null : startAt.plus(spec.matchDuration());
        this.seed = seed;
    }

    public synchronized void rematch(String playerId) {
        requireHost(playerId);
        if (status != RoomStatus.FINISHED) throw new RoomException("game is not finished");
        players.values().forEach(p -> { p.score = 0; p.finished = false; p.lastScoreAt = null; p.windowStart = null; });
        resultReported = false;
        turn = null;
        status = RoomStatus.WAITING;
        startAt = null;
        endAt = null;
        seed = 0;
    }

    public synchronized boolean play() {
        if (status != RoomStatus.COUNTDOWN) return false;
        status = RoomStatus.PLAYING;
        return true;
    }

    /** ACCEPTED 반영, IGNORED 무해해서 조용히 버림(감소·중복·초당 한도 초과), REJECTED 개연성 없음(마지막 정상값 유지) */
    public enum ScoreResult { ACCEPTED, IGNORED, REJECTED }

    /** 점수 메시지 초당 허용 개수. 계단은 한 칸마다 보내는데 사람 손은 초당 15번을 넘기 어렵다 */
    public static final int SCORE_MESSAGES_PER_SECOND = 20;
    /** 지연·재접속으로 늦게 도착한 메시지를 봐주는 여유 */
    static final Duration SCORE_SLACK = Duration.ofSeconds(2);

    public synchronized ScoreResult score(String playerId, long score, Instant now) {
        if (status != RoomStatus.PLAYING) throw new RoomException("game is not running");
        var p = requirePlayer(playerId);
        if (p.finished) return ScoreResult.IGNORED;
        if (p.overRateLimit(now)) return ScoreResult.IGNORED;
        var result = check(p, score, now);
        if (result == ScoreResult.ACCEPTED) accept(p, score, now);
        return result;
    }

    /** 최종 점수가 개연성 검사에 걸리면 마지막 정상값으로 끝낸다. 방은 계속 진행된다 */
    public synchronized ScoreResult finish(String playerId, long score, Instant now) {
        if (status != RoomStatus.PLAYING) throw new RoomException("game is not running");
        var p = requirePlayer(playerId);
        var result = check(p, score, now);
        if (result == ScoreResult.ACCEPTED) accept(p, score, now);
        p.finished = true;
        if (allFinished()) status = RoomStatus.FINISHED;
        return result;
    }

    /** 서버가 입력 로그를 재생해 확정한 점수. 개연성 검사 없이 그대로 쓰고, 중간에 올린 점수보다 낮아도 덮는다 */
    public synchronized void finishVerified(String playerId, long score) {
        if (status != RoomStatus.PLAYING) throw new RoomException("game is not running");
        var p = requirePlayer(playerId);
        p.score = score;
        p.finished = true;
        if (allFinished()) status = RoomStatus.FINISHED;
    }

    private ScoreResult check(Player p, long score, Instant now) {
        if (spec.higherIsBetter() && score <= p.score) return ScoreResult.IGNORED;
        var limits = spec.scoreLimits();
        if (limits == null) return ScoreResult.ACCEPTED;
        if (score < 0) return ScoreResult.REJECTED;
        long elapsed = Math.max(0, secondsBetween(startAt, now));
        long sinceLast = p.lastScoreAt == null ? elapsed : Math.max(0, secondsBetween(p.lastScoreAt, now));
        long slack = SCORE_SLACK.toSeconds();
        if (score > limits.maxScore()) return ScoreResult.REJECTED;
        if (score > limits.maxPerSecond() * (elapsed + slack)) return ScoreResult.REJECTED;
        long allowedJump = Math.max(limits.maxJump(), limits.maxPerSecond() * (sinceLast + slack));
        if (score - p.score > allowedJump) return ScoreResult.REJECTED;
        return ScoreResult.ACCEPTED;
    }

    private void accept(Player p, long score, Instant now) {
        p.score = score;
        p.lastScoreAt = now;
    }

    private static long secondsBetween(Instant from, Instant to) {
        return from == null ? 0 : Duration.between(from, to).toSeconds();
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
                .map(p -> new RoomSnapshot.PlayerSnapshot(p.id, p.nickname, p.character, p.score, p.finished, ranks.get(p.id), p.connected, p.userId))
                .toList();
        var info = new RoomSnapshot.GameInfo(spec.name(), spec.minPlayers(), spec.maxPlayersLimit(),
                spec.matchDuration() == null ? null : spec.matchDuration().toSeconds(), spec.optionChoices(), spec.turnBased(), spec.uniqueCharacters());
        return new RoomSnapshot(id, spec.id(), info, status, hostId, maxPlayers, Map.copyOf(options), seed, startAt, endAt, list);
    }

    private boolean allFinished() {
        return !players.isEmpty() && players.values().stream().allMatch(p -> p.finished || !p.connected);
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
        final Long userId;
        String character;
        long score;
        boolean finished;
        boolean connected = true;
        Instant lastScoreAt;
        Instant windowStart;
        int windowCount;

        Player(String id, String nickname, String character, Long userId) {
            this.id = id;
            this.nickname = nickname;
            this.character = character;
            this.userId = userId;
        }

        boolean overRateLimit(Instant now) {
            if (windowStart == null || !now.isBefore(windowStart.plusSeconds(1))) {
                windowStart = now;
                windowCount = 0;
            }
            return ++windowCount > SCORE_MESSAGES_PER_SECOND;
        }
    }
}
