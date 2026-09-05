package games.noriter.api.game.yut;

import games.noriter.api.game.yut.Board.Path;
import games.noriter.api.game.yut.YutState.Piece;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class YutRules {

    static final int FINISH = -2;

    /** 한 번의 이동 후보. via 는 방(22)에 정확히 서 있는 말이 갈 첫 칸(23 또는 27). */
    record Move(int pieceId, Throw result, Integer via, Path path, int index, int dest, int captures, int stacks) {
        Map<String, Object> view() {
            var m = new LinkedHashMap<String, Object>();
            m.put("pieceId", pieceId);
            m.put("result", result.name());
            m.put("via", via);
            m.put("dest", dest);
            m.put("captures", captures);
            m.put("stacks", stacks);
            return m;
        }
    }

    static List<Move> legalMoves(YutState s) {
        var player = s.currentPlayer();
        var out = new ArrayList<Move>();
        var results = new LinkedHashSet<>(s.queue);
        var seenGroups = new LinkedHashSet<String>();
        boolean newPieceAdded = false;
        for (var result : results) {
            newPieceAdded = false;
            seenGroups.clear();
            for (var piece : s.pieces.get(player)) {
                if (piece.finished) continue;
                if (piece.waiting()) {
                    if (result == Throw.BACKDO || newPieceAdded) continue;
                    newPieceAdded = true;
                    var target = advance(Path.RING, 0, result.steps);
                    if (target != null) out.add(describe(s, player, piece, result, null, target));
                    continue;
                }
                var key = piece.path + ":" + piece.index;
                if (!seenGroups.add(key)) continue;
                if (result == Throw.BACKDO) {
                    var target = back(piece);
                    if (target != null) out.add(describe(s, player, piece, result, null, target));
                    continue;
                }
                if (isBangStop(piece)) {
                    for (var branch : bangBranches(piece)) {
                        var target = advance(branch.path, branch.index, result.steps);
                        if (target != null) out.add(describe(s, player, piece, result, Board.node(branch.path, branch.index + 1), target));
                    }
                    continue;
                }
                var target = advance(piece.path, piece.index, result.steps);
                if (target != null) out.add(describe(s, player, piece, result, null, target));
            }
        }
        return out;
    }

    private static boolean isBangStop(Piece p) {
        return (p.path == Path.A && p.index == 3) || (p.path == Path.B && p.index == 3);
    }

    record Branch(Path path, int index) {}

    /** 방에 정확히 선 말의 갈래. 첫 번째가 직진. A(앞모도 쪽)에서 오면 직진은 속윷(23), 꺾으면 사려(27). B 는 반대. */
    private static List<Branch> bangBranches(Piece p) {
        return p.path == Path.A
                ? List.of(new Branch(Path.A, 3), new Branch(Path.D, 0))
                : List.of(new Branch(Path.B, 3), new Branch(Path.C, 0));
    }

    /** 결과: [pathOrdinal, index] 또는 완주면 [-1, -1] */
    static int[] advance(Path path, int index, int steps) {
        int next = index + steps;
        if (next > Board.lastIndex(path)) return new int[]{-1, -1};
        return new int[]{path.ordinal(), next};
    }

    static int[] back(Piece p) {
        if (p.index > 0) {
            if (p.path == Path.RING && p.index == 1) return new int[]{Path.RING.ordinal(), Board.lastIndex(Path.RING)};
            return new int[]{p.path.ordinal(), p.index - 1};
        }
        return switch (p.path) {
            case RING -> null;
            case A -> new int[]{Path.RING.ordinal(), 4};
            case B -> new int[]{Path.RING.ordinal(), 9};
            case C, D -> p.prevPath == null ? null : new int[]{p.prevPath.ordinal(), p.prevIndex - 1};
        };
    }

    private static Move describe(YutState s, String player, Piece piece, Throw result, Integer via, int[] target) {
        if (target[0] < 0) return new Move(piece.id, result, via, null, -1, FINISH, 0, 0);
        var path = Path.values()[target[0]];
        int dest = Board.node(path, target[1]);
        int captures = 0, stacks = 0;
        for (var e : s.pieces.entrySet()) {
            for (var q : e.getValue()) {
                if (!q.onBoard() || q == piece || q.node() != dest) continue;
                if (e.getKey().equals(player)) stacks++; else captures++;
            }
        }
        return new Move(piece.id, result, via, path, target[1], dest, captures, stacks);
    }

    /** 이동 적용. 잡았으면 true. */
    static boolean applyMove(YutState s, Move mv) {
        var player = s.currentPlayer();
        var mover = s.pieces.get(player).stream().filter(p -> p.id == mv.pieceId()).findFirst().orElseThrow();
        var group = new ArrayList<Piece>();
        if (mover.onBoard()) {
            for (var p : s.pieces.get(player)) if (p.onBoard() && p.path == mover.path && p.index == mover.index) group.add(p);
        } else {
            group.add(mover);
        }
        if (mv.dest() == FINISH) {
            for (var p : group) { p.finished = true; p.path = null; }
            return false;
        }
        Path path = mv.path();
        int index = mv.index();
        Path prevPath = mover.path;
        int prevIndex = mover.index;
        boolean branched = mv.via() != null && (path == Path.C || path == Path.D);
        Piece resident = null;
        for (var p : s.pieces.get(player)) {
            if (p.onBoard() && !group.contains(p) && p.node() == mv.dest()) { resident = p; break; }
        }
        if (resident != null) { path = resident.path; index = resident.index; prevPath = resident.prevPath; prevIndex = resident.prevIndex; branched = false; }
        boolean captured = false;
        for (var e : s.pieces.entrySet()) {
            if (e.getKey().equals(player)) continue;
            for (var q : e.getValue()) if (q.onBoard() && q.node() == mv.dest()) { q.home(); captured = true; }
        }
        for (var p : group) {
            if (branched) { p.prevPath = prevPath; p.prevIndex = prevIndex; }
            else if (resident != null) { p.prevPath = prevPath; p.prevIndex = prevIndex; }
            p.path = path;
            p.index = index;
            if (path == Path.RING && index == Board.MO) { p.path = Path.A; p.index = 0; }
            else if (path == Path.RING && index == Board.BACK_MO) { p.path = Path.B; p.index = 0; }
        }
        return captured;
    }
}
