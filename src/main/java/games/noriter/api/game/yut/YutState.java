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

    enum Phase { THROW, MOVE, CARD, ENDED }

    enum CardTrigger { OPENING, CAPTURE, BANG }

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

    /** 던진 결과 한 개. steps 는 카드(욕심 부려)로 바뀔 수 있다. */
    record Rolled(Throw result, int steps) {
        static Rolled of(Throw t) { return new Rolled(t, t.steps); }
        Map<String, Object> view() { return Map.of("result", result.name(), "steps", steps); }
    }

    /** 플레이어별 카드 효과. turnNo 는 실제로 시작한 차례 수. */
    static final class Effects {
        int turnNo;
        int extraThrows;
        boolean chooseThrow;
        boolean forcedBackdo;
        boolean stepBonus;
        boolean shortcutOpen;
        boolean skipNext;
        int backdoImmuneUntil = -1;
        int shieldUntil = -1;
        int targetUntil = -1;

        boolean shield() { return turnNo < shieldUntil; }
        boolean target() { return turnNo < targetUntil; }
        boolean backdoImmune() { return turnNo <= backdoImmuneUntil; }

        List<Map<String, Object>> view() {
            var out = new ArrayList<Map<String, Object>>();
            if (extraThrows > 0) out.add(badge("ONE_MORE", "한 번 더 ×" + extraThrows));
            if (chooseThrow) out.add(badge("CHOOSE_THROW", "골라 던지기"));
            if (forcedBackdo) out.add(badge("CURSED_BACKDO", "저주의 빽도"));
            if (stepBonus) out.add(badge("GREED", "칸수 +1"));
            if (shortcutOpen) out.add(badge("SHORTCUT", "지름길 열림"));
            if (skipNext) out.add(badge("REST", "다음 차례 쉼"));
            if (backdoImmune()) out.add(badge("BACKDO_IMMUNE", "빽도 무효"));
            if (shield()) out.add(badge("SHIELD", "보호막"));
            if (target()) out.add(badge("TARGET", "표적"));
            return out;
        }

        private static Map<String, Object> badge(String id, String label) {
            return Map.of("id", id, "label", label);
        }
    }

    /** 카드 고르는 중인 상황. */
    static final class CardDraw {
        final String player;
        final CardTrigger trigger;
        final List<Card> pile;
        final Integer moverPieceId;
        final List<CapturedPiece> captured;
        final int captureBonus;

        CardDraw(String player, CardTrigger trigger, List<Card> pile, Integer moverPieceId, List<CapturedPiece> captured, int captureBonus) {
            this.player = player;
            this.trigger = trigger;
            this.pile = pile;
            this.moverPieceId = moverPieceId;
            this.captured = captured;
            this.captureBonus = captureBonus;
        }
    }

    record CapturedPiece(String owner, int pieceId, Board.Path path, int index, Board.Path prevPath, int prevIndex) {}

    final List<String> players;
    final Map<String, List<Piece>> pieces = new LinkedHashMap<>();
    final Map<String, Effects> effects = new LinkedHashMap<>();
    final Set<String> bots = new HashSet<>();
    final List<String> finishedOrder = new ArrayList<>();
    /** 항복한 순서. 먼저 항복한 사람이 더 아래 순위. */
    final List<String> resigned = new ArrayList<>();
    final Random rng;
    final boolean backdo;
    final boolean cards;
    final int pieceCount;
    final int turnSeconds;
    final int cardSeconds;
    final int botDelaySeconds;

    int turn;
    Phase phase = Phase.THROW;
    final List<Rolled> queue = new ArrayList<>();
    boolean[] sticks = new boolean[4];
    int bonusThrows;
    /** 같은 결과가 연달아 나와도 클라이언트가 새 던지기로 구분하도록 매번 올린다 */
    int throwSeq;
    /** 직전 던지기가 윷·모라 다음 던지기는 보너스를 소모하지 않는 공짜 던지기 */
    boolean freeThrow;
    Instant deadline;
    Map<String, Object> lastEvent = Map.of("type", "start");
    CardDraw cardDraw;
    final List<String> openingQueue = new ArrayList<>();
    boolean opening;
    /** 카드 단계가 끝난 뒤 이어서 할 일. move 뒤였으면 true. */
    boolean resumeAfterMove;

    YutState(long seed, List<String> players, boolean backdo, boolean cards, int pieceCount, int turnSeconds, int cardSeconds, int botDelaySeconds) {
        this.rng = new Random(seed);
        this.players = List.copyOf(players);
        this.backdo = backdo;
        this.cards = cards;
        this.pieceCount = pieceCount;
        this.turnSeconds = turnSeconds;
        this.cardSeconds = cardSeconds;
        this.botDelaySeconds = botDelaySeconds;
        for (var p : this.players) {
            var list = new ArrayList<Piece>();
            for (int i = 0; i < pieceCount; i++) list.add(new Piece(i));
            pieces.put(p, list);
            effects.put(p, new Effects());
        }
    }

    @Override public boolean ended() { return phase == Phase.ENDED; }
    @Override public String currentPlayer() { return players.get(turn); }
    @Override public boolean isBot(String playerId) { return bots.contains(playerId); }
    @Override public Instant deadline() { return ended() ? null : deadline; }

    /** 지금 행동해야 하는 사람. 카드 단계면 카드 뽑는 사람. */
    String actor() {
        return phase == Phase.CARD && cardDraw != null ? cardDraw.player : currentPlayer();
    }

    Effects effects(String player) { return effects.get(player); }

    Piece piece(String player, int id) {
        return pieces.get(player).stream().filter(p -> p.id == id).findFirst().orElseThrow();
    }

    List<Piece> stackOf(Piece p) {
        var out = new ArrayList<Piece>();
        if (!p.onBoard()) { out.add(p); return out; }
        for (var e : pieces.entrySet()) {
            for (var q : e.getValue()) if (q.onBoard() && q.path == p.path && q.index == p.index && ownerOf(q).equals(ownerOf(p))) out.add(q);
        }
        return out;
    }

    String ownerOf(Piece p) {
        for (var e : pieces.entrySet()) if (e.getValue().contains(p)) return e.getKey();
        throw new IllegalStateException();
    }

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
        rest.removeAll(resigned);
        rest.sort(Comparator.comparingInt((String p) -> -finishedCount(p)).thenComparingInt(this::remaining));
        var out = new ArrayList<>(finishedOrder);
        out.addAll(rest);
        var quitters = new ArrayList<>(resigned);
        java.util.Collections.reverse(quitters);
        out.addAll(quitters);
        return out;
    }

    List<String> activePlayers() {
        var out = new ArrayList<>(players);
        out.removeAll(resigned);
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
            pm.put("resigned", resigned.contains(player));
            pm.put("effects", effects.get(player).view());
            ps.add(pm);
        }
        v.put("players", ps);
        v.put("turn", currentPlayer());
        v.put("actor", actor());
        v.put("phase", phase.name());
        v.put("queue", queue.stream().map(Rolled::view).toList());
        v.put("sticks", List.of(sticks[0], sticks[1], sticks[2], sticks[3]));
        v.put("bonusThrows", bonusThrows);
        v.put("chooseThrow", phase == Phase.THROW && effects.get(currentPlayer()).chooseThrow && !effects.get(currentPlayer()).forcedBackdo);
        v.put("deadline", deadline == null ? null : deadline.toString());
        v.put("legalMoves", phase == Phase.MOVE ? YutRules.legalMoves(this).stream().map(YutRules.Move::view).toList() : List.of());
        if (phase == Phase.CARD && cardDraw != null) {
            var c = new LinkedHashMap<String, Object>();
            c.put("player", cardDraw.player);
            c.put("trigger", cardDraw.trigger.name());
            c.put("size", cardDraw.pile.size());
            v.put("card", c);
        } else {
            v.put("card", null);
        }
        v.put("lastEvent", lastEvent);
        v.put("ended", ended());
        v.put("ranking", ranking());
        v.put("finishedOrder", finishedOrder);
        v.put("options", Map.of("backdo", backdo, "cards", cards, "pieces", pieceCount, "turnSeconds", turnSeconds, "cardSeconds", cardSeconds));
        v.put("names", Board.NAMES);
        return v;
    }
}
