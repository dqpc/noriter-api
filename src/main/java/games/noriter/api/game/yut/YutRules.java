package games.noriter.api.game.yut;

import games.noriter.api.game.yut.Board.Path;
import games.noriter.api.game.yut.YutState.CapturedPiece;
import games.noriter.api.game.yut.YutState.Piece;
import games.noriter.api.game.yut.YutState.Rolled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class YutRules {

    static final int FINISH = -2;

    /**
     * 한 번의 이동 후보. via 는 갈림길에서 꺾을 때 들어서는 첫 칸(20·25·23·27).
     * blocked 는 보호막 때문에 잡지 못하고 같이 서게 되는 상대 말 수.
     */
    record Move(int pieceId, Throw result, int steps, Integer via, Path path, int index, int dest, int captures, int stacks, int blocked) {
        Map<String, Object> view() {
            var m = new LinkedHashMap<String, Object>();
            m.put("pieceId", pieceId);
            m.put("result", result.name());
            m.put("steps", steps);
            m.put("via", via);
            m.put("dest", dest);
            m.put("captures", captures);
            m.put("stacks", stacks);
            m.put("blocked", blocked);
            return m;
        }

        boolean matches(int pieceId, Throw result, int steps, Integer via) {
            return this.pieceId == pieceId && this.result == result && this.steps == steps && java.util.Objects.equals(this.via, via);
        }
    }

    record MoveResult(List<CapturedPiece> captured, boolean finished) {
        boolean capturedAny() { return !captured.isEmpty(); }
    }

    static List<Move> legalMoves(YutState s) {
        var player = s.currentPlayer();
        var out = new ArrayList<Move>();
        var rolls = new LinkedHashSet<>(s.queue);
        var seenGroups = new LinkedHashSet<String>();
        boolean shortcut = s.effects(player).shortcutOpen;
        for (var roll : rolls) {
            boolean newPieceAdded = false;
            seenGroups.clear();
            for (var piece : s.pieces.get(player)) {
                if (piece.finished) continue;
                if (piece.waiting()) {
                    if (roll.result() == Throw.BACKDO || newPieceAdded) continue;
                    newPieceAdded = true;
                    var target = advance(Path.RING, 0, roll.steps());
                    if (target != null) out.add(describe(s, player, piece, roll, null, target));
                    continue;
                }
                var key = piece.path + ":" + piece.index;
                if (!seenGroups.add(key)) continue;
                if (roll.result() == Throw.BACKDO) {
                    var target = back(piece);
                    if (target != null) out.add(describe(s, player, piece, roll, null, target));
                    continue;
                }
                if (isBangStop(piece)) {
                    for (var branch : bangBranches(piece)) {
                        var target = advance(branch.path, branch.index, roll.steps());
                        if (target != null) out.add(describe(s, player, piece, roll, Board.node(branch.path, branch.index + 1), target));
                    }
                    continue;
                }
                var target = advance(piece.path, piece.index, roll.steps());
                if (target != null) out.add(describe(s, player, piece, roll, null, target));
                if (shortcut) {
                    var alt = passThroughBranch(piece, roll.steps());
                    if (alt != null) out.add(describe(s, player, piece, roll, alt.via, alt.target));
                }
            }
        }
        return out;
    }

    static boolean isBangStop(Piece p) {
        return (p.path == Path.A && p.index == 3) || (p.path == Path.B && p.index == 3);
    }

    record Branch(Path path, int index) {}

    /** 방에 정확히 선 말의 갈래. 첫 번째가 직진. A(앞모도 쪽)에서 오면 직진은 속윷(23), 꺾으면 사려(27). B 는 반대. */
    private static List<Branch> bangBranches(Piece p) {
        return p.path == Path.A
                ? List.of(new Branch(Path.A, 3), new Branch(Path.D, 0))
                : List.of(new Branch(Path.B, 3), new Branch(Path.C, 0));
    }

    record PassThrough(int via, int[] target) {}

    /** 지름길 열려: 이동 중 처음 지나치는 갈림길(5·10·방)에서 꺾은 경우의 목적지. 갈림길에 정확히 서면 일반 규칙이라 null. */
    static PassThrough passThroughBranch(Piece p, int steps) {
        for (int k = 1; k < steps; k++) {
            int index = p.index + k;
            if (index > Board.lastIndex(p.path)) return null;
            int node = Board.node(p.path, index);
            Branch branch = null;
            if (p.path == Path.RING && node == Board.MO) branch = new Branch(Path.A, 0);
            else if (p.path == Path.RING && node == Board.BACK_MO) branch = new Branch(Path.B, 0);
            else if (node == Board.BANG && p.path == Path.A) branch = new Branch(Path.D, 0);
            else if (node == Board.BANG && p.path == Path.B) branch = new Branch(Path.C, 0);
            if (branch == null) continue;
            var target = advance(branch.path, branch.index, steps - k);
            return target == null ? null : new PassThrough(Board.node(branch.path, 1), target);
        }
        return null;
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

    static Move describe(YutState s, String player, Piece piece, Rolled roll, Integer via, int[] target) {
        if (target[0] < 0) return new Move(piece.id, roll.result(), roll.steps(), via, null, -1, FINISH, 0, 0, 0);
        var path = Path.values()[target[0]];
        int dest = Board.node(path, target[1]);
        int captures = 0, stacks = 0, blocked = 0;
        for (var e : s.pieces.entrySet()) {
            for (var q : e.getValue()) {
                if (!q.onBoard() || q == piece || q.node() != dest) continue;
                if (e.getKey().equals(player)) stacks++;
                else if (s.effects(e.getKey()).shield()) blocked++;
                else captures++;
            }
        }
        return new Move(piece.id, roll.result(), roll.steps(), via, path, target[1], dest, captures, stacks, blocked);
    }

    /** 카드 효과 등으로 만든 이동. 결과 표시는 DO 로 두고 steps 로 칸수를 적는다. */
    static Move synthetic(YutState s, String player, Piece piece, int[] target, int steps) {
        return describe(s, player, piece, new Rolled(Throw.DO, steps), null, target);
    }

    static MoveResult applyMove(YutState s, String player, Move mv) {
        return applyMove(s, player, mv, false);
    }

    /** 이동 적용. single 이면 업힌 무리 중 그 말 하나만 움직인다. */
    static MoveResult applyMove(YutState s, String player, Move mv, boolean single) {
        var mover = s.piece(player, mv.pieceId());
        var group = new ArrayList<Piece>();
        if (mover.onBoard() && !single) {
            for (var p : s.pieces.get(player)) if (p.onBoard() && p.path == mover.path && p.index == mover.index) group.add(p);
        } else {
            group.add(mover);
        }
        if (mv.dest() == FINISH) {
            for (var p : group) { p.finished = true; p.path = null; }
            return new MoveResult(List.of(), true);
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
        var captured = new ArrayList<CapturedPiece>();
        for (var e : s.pieces.entrySet()) {
            if (e.getKey().equals(player) || s.effects(e.getKey()).shield()) continue;
            for (var q : e.getValue()) {
                if (q.onBoard() && q.node() == mv.dest()) {
                    captured.add(new CapturedPiece(e.getKey(), q.id, q.path, q.index, q.prevPath, q.prevIndex));
                    q.home();
                }
            }
        }
        for (var p : group) {
            if (branched || resident != null) { p.prevPath = prevPath; p.prevIndex = prevIndex; }
            p.path = path;
            p.index = index;
            if (path == Path.RING && index == Board.MO) { p.path = Path.A; p.index = 0; }
            else if (path == Path.RING && index == Board.BACK_MO) { p.path = Path.B; p.index = 0; }
        }
        return new MoveResult(captured, false);
    }

    private YutRules() {}
}
