package games.noriter.api.game2048;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 웹 games/2048/logic.test.ts 의 케이스를 그대로 옮긴 것 */
class Board2048Tests {

    static final int[][] BOARD = {
            {2, 0, 0, 2},
            {0, 4, 0, 0},
            {0, 0, 0, 0},
            {2, 0, 0, 0},
    };

    static Board2048 board(int[][] cells) {
        return new Board2048(1, 2048, cells);
    }

    @Test
    void slideSkipsEmptyAndMergesOnce() {
        assertThat(Board2048.slide(new int[] {0, 2, 0, 2}).row()).containsExactly(4, 0, 0, 0);
        var four = Board2048.slide(new int[] {2, 2, 2, 2});
        assertThat(four.row()).containsExactly(4, 4, 0, 0);
        assertThat(four.gained()).isEqualTo(8);
        var mixed = Board2048.slide(new int[] {4, 4, 8, 0});
        assertThat(mixed.row()).containsExactly(8, 8, 0, 0);
        assertThat(mixed.gained()).isEqualTo(8);
        assertThat(Board2048.slide(new int[] {2, 4, 8, 16}).row()).containsExactly(2, 4, 8, 16);
    }

    /** step 은 새 타일을 하나 놓으므로 합쳐진 타일이 놓인 칸만 본다 (빈 칸에만 놓이니 덮어쓰지 않는다) */
    @Test
    void movesInFourDirections() {
        var left = board(BOARD);
        assertThat(left.step('2')).isTrue();
        var l = left.cells();
        assertThat(new int[] {l[0], l[4], l[12]}).containsExactly(4, 4, 2);
        assertThat(left.score()).isEqualTo(4);

        var right = board(BOARD);
        assertThat(right.step('3')).isTrue();
        var r = right.cells();
        assertThat(new int[] {r[3], r[7], r[15]}).containsExactly(4, 4, 2);

        var up = board(BOARD);
        assertThat(up.step('0')).isTrue();
        var u = up.cells();
        assertThat(new int[] {u[0], u[1], u[3]}).containsExactly(4, 4, 2);

        var down = board(BOARD);
        assertThat(down.step('1')).isTrue();
        var d = down.cells();
        assertThat(new int[] {d[12], d[13], d[15]}).containsExactly(4, 4, 2);
        assertThat(down.score()).isEqualTo(4);
    }

    @Test
    void noMovementReturnsFalseAndUsesNoRandom() {
        int[][] stuck = {{2, 4, 8, 16}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        var b = board(stuck);
        assertThat(b.step('2')).isFalse();
        assertThat(b.step('0')).isFalse();
        assertThat(b.cells()).containsExactly(2, 4, 8, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(b.step('x')).isFalse();
    }

    @Test
    void canMoveRules() {
        assertThat(board(new int[4][4]).canMove()).isTrue();
        assertThat(board(new int[][] {{2, 4, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 4}}).canMove()).isTrue();
        assertThat(board(new int[][] {{2, 4, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 2}}).canMove()).isFalse();
    }

    @Test
    void newBoardStartsWithTwoTilesAndStepSpawnsOne() {
        var b = new Board2048(7, 2048);
        assertThat(java.util.Arrays.stream(b.cells()).filter(v -> v != 0).count()).isEqualTo(2);
        assertThat(b.score()).isZero();

        var s = board(new int[][] {{2, 2, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        assertThat(s.step('2')).isTrue();
        assertThat(s.score()).isEqualTo(4);
        assertThat(java.util.Arrays.stream(s.cells()).filter(v -> v != 0).count()).isEqualTo(2);
    }

    @Test
    void reachingTargetWinsAndFreezes() {
        var b = new Board2048(1, 8, new int[][] {{4, 4, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
        assertThat(b.step('2')).isTrue();
        assertThat(b.won()).isTrue();
        assertThat(b.ended()).isTrue();
        assertThat(b.step('3')).isFalse();
    }

    @Test
    void mulberry32MatchesWeb() {
        // 웹 mulberry32(1) 의 처음 세 값 (node 로 계산)
        var rng = new Mulberry32(1);
        assertThat(rng.next()).isEqualTo(0.6270739405881613);
        assertThat(rng.next()).isEqualTo(0.002735721180215478);
        assertThat(rng.next()).isEqualTo(0.5274470399599522);
    }
}
