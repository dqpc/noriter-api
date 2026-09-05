package games.noriter.api.game.yut;

import games.noriter.api.game.TurnState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class YutState implements TurnState {

    enum Phase { THROW, MOVE, ENDED }

    static final class Piece {
        final int id;
        Board.Path path;
        int index;
        Board.Path prevPath;
        int prevIndex;
        boolean finished;

        Piece(int id) { this.id = id; }

        boolean onBoard() { return path != null && !finished; }
        boolean waiting() { return path == null && !finished; }
        int node() { return onBoard() ? Board.node(path, index) : -1; }

        void home() { path = null; index = 0; prevPath = null; finished = false; }
    }

    final List<String> players;
    final Map<String, List<Piece>> pieces = new LinkedHashMap<>();
    final Set<String> bots = new HashSet<>();
    final List<String> finishedOrder = new ArrayList<>();
    final Random rng;
    final boolean backdo;
    final int pieceCount;
    final int turnSeconds;
    final int botDelaySeconds;

    int turn;
    Phase phase = Phase.THROW;
    final List<Throw> queue = new ArrayList<>();
    boolean[] sticks = new boolean[4];
    int bonusThrows;
    Instant deadline;
    Map<String, Object> lastEvent = Map.of("type", "start");

    YutState(long seed, List<String> players, boolean backdo, int pieceCount, int turnSeconds, int botDelaySeconds) {
        this.rng = new Random(seed);
        this.players = List.copyOf(players);
        this.backdo = backdo;
        this.pieceCount = pieceCount;
        this.turnSeconds = turnSeconds;
        this.botDelaySeconds = botDelaySeconds;
        for (var p : this.players) {
            var list = new ArrayList<Piece>();
            for (int i = 0; i < pieceCount; i++) list.add(new Piece(i));
            pieces.put(p, list);
        }
    }

    @Override public boolean ended() { return phase == Phase.ENDED; }
    @Override public String currentPlayer() { return players.get(turn); }
    @Override public boolean isBot(String playerId) { return bots.contains(playerId); }
    @Override public Instant deadline() { return ended() ? null : deadline; }

    int remaining(String player) {
        int total = 0;
        for (var p : pieces.get(player)) {
            if (p.finished) continue;
            total += p.onBoard() ? Board.lastIndex(p.path) - p.index + 1 : Board.lastIndex(Board.Path.RING) + 1;
        }
        return total;
    }

    int finishedCount(String player) {
        return (int) pieces.get(player).stream().filter(p -> p.finished).count();
    }

    @Override
    public List<String> ranking() {
        var rest = new ArrayList<>(players);
        rest.removeAll(finishedOrder);
        rest.sort(Comparator.comparingInt((String p) -> -finishedCount(p)).thenComparingInt(this::remaining));
        var out = new ArrayList<>(finishedOrder);
        out.addAll(rest);
        return out;
    }

    @Override
    public Map<String, Long> scores() {
        var out = new LinkedHashMap<String, Long>();
        var ranking = ranking();
        for (int i = 0; i < ranking.size(); i++) out.put(ranking.get(i), (long) (ranking.size() - i));
        return out;
    }

    @Override
    public Map<String, Object> view() {
        var v = new LinkedHashMap<String, Object>();
        var ps = new ArrayList<Map<String, Object>>();
        for (var player : players) {
            var pl = new ArrayList<Map<String, Object>>();
            for (var p : pieces.get(player)) {
                var m = new LinkedHashMap<String, Object>();
                m.put("id", p.id);
                m.put("node", p.node());
                m.put("path", p.path == null ? null : p.path.name());
                m.put("index", p.index);
                m.put("finished", p.finished);
                pl.add(m);
            }
            var pm = new LinkedHashMap<String, Object>();
            pm.put("id", player);
            pm.put("pieces", pl);
            pm.put("finished", finishedCount(player));
            pm.put("bot", bots.contains(player));
            ps.add(pm);
        }
        v.put("players", ps);
        v.put("turn", currentPlayer());
        v.put("phase", phase.name());
        v.put("queue", queue.stream().map(t -> t.name()).toList());
        v.put("sticks", List.of(sticks[0], sticks[1], sticks[2], sticks[3]));
        v.put("bonusThrows", bonusThrows);
        v.put("deadline", deadline == null ? null : deadline.toString());
        v.put("legalMoves", phase == Phase.MOVE ? YutRules.legalMoves(this).stream().map(YutRules.Move::view).toList() : List.of());
        v.put("lastEvent", lastEvent);
        v.put("ended", ended());
        v.put("ranking", ranking());
        v.put("finishedOrder", finishedOrder);
        v.put("options", Map.of("backdo", backdo, "pieces", pieceCount, "turnSeconds", turnSeconds));
        v.put("names", Board.NAMES);
        return v;
    }
}
