package games.noriter.api.game2048;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 웹 games/2048/logic.ts 의 Java 포팅. 이동·합치기·새 타일·점수 규칙이 같아야 재생 결과가 웹과 일치한다.
 * 입력 코드: 0 위, 1 아래, 2 왼쪽, 3 오른쪽 (웹 DIRECTION_CODE).
 */
public final class Board2048 {

    public static final int SIZE = 4;
    public static final int DEFAULT_TARGET = 2048;

    private final int[][] board = new int[SIZE][SIZE];
    private final int target;
    private final Mulberry32 rng;
    private long score;
    private boolean won;
    private boolean over;

    public Board2048(long seed, int target) {
        this.target = target;
        this.rng = new Mulberry32(seed);
        spawn();
        spawn();
    }

    /** 테스트용: 정해진 판에서 시작 */
    Board2048(long seed, int target, int[][] cells) {
        this.target = target;
        this.rng = new Mulberry32(seed);
        for (int r = 0; r < SIZE; r++) board[r] = cells[r].clone();
    }

    public long score() { return score; }
    public boolean won() { return won; }
    public boolean over() { return over; }
    public boolean ended() { return won || over; }

    public int[] cells() {
        return Arrays.stream(board).flatMapToInt(Arrays::stream).toArray();
    }

    /** 한 수 적용. 끝난 판이거나 움직임이 없거나 코드가 잘못되면 false 이고 난수도 쓰지 않는다. */
    public boolean step(char code) {
        if (ended() || code < '0' || code > '3') return false;
        int dir = code - '0';
        int[][] next = new int[SIZE][SIZE];
        long gained = 0;
        for (int i = 0; i < SIZE; i++) {
            int[] line = new int[SIZE];
            for (int j = 0; j < SIZE; j++) {
                int[] rc = toCell(dir, i, j);
                line[j] = board[rc[0]][rc[1]];
            }
            var slid = slide(line);
            gained += slid.gained();
            for (int j = 0; j < SIZE; j++) {
                int[] rc = toCell(dir, i, j);
                next[rc[0]][rc[1]] = slid.row()[j];
            }
        }
        if (Arrays.deepEquals(board, next)) return false;
        for (int r = 0; r < SIZE; r++) board[r] = next[r];
        spawn();
        score += gained;
        won = maxTile() >= target;
        over = !canMove();
        return true;
    }

    record Slide(int[] row, int gained) {}

    /** 웹 slideRow: 빈 칸을 건너뛰고 앞으로 밀며 같은 값은 한 번만 합친다 */
    static Slide slide(int[] row) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < row.length; i++) if (row[i] != 0) idx.add(i);
        int[] out = new int[row.length];
        int n = 0;
        int gained = 0;
        for (int i = 0; i < idx.size(); i++) {
            int a = idx.get(i);
            if (i + 1 < idx.size() && row[a] == row[idx.get(i + 1)]) {
                out[n++] = row[a] * 2;
                gained += row[a] * 2;
                i++;
            } else {
                out[n++] = row[a];
            }
        }
        return new Slide(out, gained);
    }

    private static int[] toCell(int dir, int i, int j) {
        return switch (dir) {
            case 0 -> new int[] {j, i};
            case 1 -> new int[] {SIZE - 1 - j, i};
            case 2 -> new int[] {i, j};
            default -> new int[] {i, SIZE - 1 - j};
        };
    }

    /** 웹 pickSpawn 과 같은 순서로 난수를 두 번 쓴다: 칸 선택, 2/4 선택 */
    private void spawn() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) if (board[r][c] == 0) empty.add(new int[] {r, c});
        if (empty.isEmpty()) return;
        int[] cell = empty.get((int) (rng.next() * empty.size()));
        board[cell[0]][cell[1]] = rng.next() < 0.9 ? 2 : 4;
    }

    boolean canMove() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int v = board[r][c];
                if (v == 0) return true;
                if (c + 1 < SIZE && board[r][c + 1] == v) return true;
                if (r + 1 < SIZE && board[r + 1][c] == v) return true;
            }
        }
        return false;
    }

    int maxTile() {
        return Arrays.stream(cells()).max().orElse(0);
    }
}
