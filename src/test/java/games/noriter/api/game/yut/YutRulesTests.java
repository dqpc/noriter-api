package games.noriter.api.game.yut;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.game.yut.Board.Path;
import games.noriter.api.game.yut.YutRules.Move;
import java.util.List;
import org.junit.jupiter.api.Test;

class YutRulesTests {

    YutState state(String... players) {
        return new YutState(1, List.of(players), true, 3, 30, 1);
    }

    YutState.Piece piece(YutState s, String player, int id) {
        return s.pieces.get(player).get(id);
    }

    void place(YutState.Piece p, Path path, int index) {
        p.path = path;
        p.index = index;
    }

    Move find(List<Move> moves, int pieceId, Throw t, Integer via) {
        return moves.stream().filter(m -> m.pieceId() == pieceId && m.result() == t && java.util.Objects.equals(m.via(), via)).findFirst().orElseThrow();
    }

    @Test
    void throwMapping() {
        assertThat(Throw.of(new boolean[]{false, false, false, false}, true)).isEqualTo(Throw.MO);
        assertThat(Throw.of(new boolean[]{true, true, true, true}, true)).isEqualTo(Throw.YUT);
        assertThat(Throw.of(new boolean[]{true, false, false, false}, true)).isEqualTo(Throw.BACKDO);
        assertThat(Throw.of(new boolean[]{true, false, false, false}, false)).isEqualTo(Throw.DO);
        assertThat(Throw.of(new boolean[]{false, true, false, false}, true)).isEqualTo(Throw.DO);
        assertThat(Throw.of(new boolean[]{true, true, false, false}, true)).isEqualTo(Throw.GAE);
        assertThat(Throw.of(new boolean[]{true, true, true, false}, true)).isEqualTo(Throw.GEOL);
    }

    @Test
    void newPieceEntersRingAndOnlyOneNewPieceOptionPerResult() {
        var s = state("a", "b");
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.GAE);
        var moves = YutRules.legalMoves(s);
        assertThat(moves).hasSize(1);
        assertThat(moves.get(0).dest()).isEqualTo(2);
        YutRules.applyMove(s, moves.get(0));
        assertThat(piece(s, "a", 0).node()).isEqualTo(2);
        assertThat(piece(s, "a", 0).path).isEqualTo(Path.RING);
    }

    @Test
    void landingExactlyOnMoSwitchesToShortcutPath() {
        var s = state("a", "b");
        place(piece(s, "a", 0), Path.RING, 2);
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.GEOL);
        YutRules.applyMove(s, find(YutRules.legalMoves(s), 0, Throw.GEOL, null));
        var p = piece(s, "a", 0);
        assertThat(p.node()).isEqualTo(Board.MO);
        assertThat(p.path).isEqualTo(Path.A);
        assertThat(p.index).isEqualTo(0);
        s.queue.clear();
        s.queue.add(Throw.DO);
        assertThat(find(YutRules.legalMoves(s), 0, Throw.DO, null).dest()).isEqualTo(20);
    }

    @Test
    void passingMoStaysOnRing() {
        var s = state("a", "b");
        place(piece(s, "a", 0), Path.RING, 3);
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.GEOL);
        YutRules.applyMove(s, find(YutRules.legalMoves(s), 0, Throw.GEOL, null));
        assertThat(piece(s, "a", 0).node()).isEqualTo(6);
        assertThat(piece(s, "a", 0).path).isEqualTo(Path.RING);
    }

    @Test
    void bangStopOffersTwoBranchesAndStraightThroughWhenPassing() {
        var s = state("a", "b");
        place(piece(s, "a", 0), Path.A, 3);
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.GAE);
        var moves = YutRules.legalMoves(s).stream().filter(m -> m.pieceId() == 0).toList();
        assertThat(moves).extracting(Move::via).containsExactlyInAnyOrder(23, 27);
        assertThat(find(moves, 0, Throw.GAE, 27).dest()).isEqualTo(28);
        assertThat(find(moves, 0, Throw.GAE, 23).dest()).isEqualTo(24);

        var t = state("a", "b");
        place(piece(t, "a", 0), Path.A, 2);
        t.phase = YutState.Phase.MOVE;
        t.queue.add(Throw.GAE);
        var m = YutRules.legalMoves(t).stream().filter(x -> x.pieceId() == 0).toList();
        assertThat(m).hasSize(1);
        assertThat(m.get(0).dest()).isEqualTo(23);
    }

    @Test
    void backdoCases() {
        var s = state("a", "b");
        place(piece(s, "a", 0), Path.RING, 1);
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.BACKDO);
        var mv = find(YutRules.legalMoves(s), 0, Throw.BACKDO, null);
        assertThat(mv.dest()).isEqualTo(0);
        YutRules.applyMove(s, mv);
        assertThat(piece(s, "a", 0).index).isEqualTo(Board.lastIndex(Path.RING));
        s.queue.clear();
        s.queue.add(Throw.DO);
        assertThat(find(YutRules.legalMoves(s), 0, Throw.DO, null).dest()).isEqualTo(YutRules.FINISH);

        var t = state("a", "b");
        place(piece(t, "a", 0), Path.A, 0);
        t.phase = YutState.Phase.MOVE;
        t.queue.add(Throw.BACKDO);
        assertThat(find(YutRules.legalMoves(t), 0, Throw.BACKDO, null).dest()).isEqualTo(4);

        var u = state("a", "b");
        u.phase = YutState.Phase.MOVE;
        u.queue.add(Throw.BACKDO);
        assertThat(YutRules.legalMoves(u)).isEmpty();
    }

    @Test
    void captureSendsOpponentHomeAndStackingAdoptsResidentPath() {
        var s = state("a", "b");
        place(piece(s, "b", 0), Path.RING, 3);
        place(piece(s, "a", 0), Path.RING, 1);
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.GAE);
        var mv = find(YutRules.legalMoves(s), 0, Throw.GAE, null);
        assertThat(mv.captures()).isEqualTo(1);
        assertThat(YutRules.applyMove(s, mv)).isTrue();
        assertThat(piece(s, "b", 0).waiting()).isTrue();

        place(piece(s, "a", 1), Path.RING, 1);
        s.queue.clear();
        s.queue.add(Throw.GAE);
        var stack = find(YutRules.legalMoves(s), 1, Throw.GAE, null);
        assertThat(stack.stacks()).isEqualTo(1);
        YutRules.applyMove(s, stack);
        assertThat(piece(s, "a", 1).node()).isEqualTo(3);
        s.queue.clear();
        s.queue.add(Throw.GAE);
        var group = YutRules.legalMoves(s).stream().filter(m -> m.result() == Throw.GAE && m.dest() == 5).toList();
        assertThat(group).hasSize(1);
        YutRules.applyMove(s, group.get(0));
        assertThat(piece(s, "a", 0).node()).isEqualTo(5);
        assertThat(piece(s, "a", 1).node()).isEqualTo(5);
    }

    @Test
    void overshootFinishes() {
        var s = state("a", "b");
        place(piece(s, "a", 0), Path.D, 2);
        s.phase = YutState.Phase.MOVE;
        s.queue.add(Throw.MO);
        var mv = find(YutRules.legalMoves(s), 0, Throw.MO, null);
        assertThat(mv.dest()).isEqualTo(YutRules.FINISH);
        YutRules.applyMove(s, mv);
        assertThat(piece(s, "a", 0).finished).isTrue();
        assertThat(s.finishedCount("a")).isEqualTo(1);
    }
}
