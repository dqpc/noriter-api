package games.noriter.api.game.yut;

import java.util.List;
import java.util.Map;

/**
 * 29칸. 0 참먹이(출발·도착) → 1~4 도개걸윷 → 5 모 → 6~9 → 10 뒷모 → 11~14 → 15 찌모 → 16~19 → 0.
 * 대각선: 5→20 앞모도→21 앞모개→22 방→23 속윷→24 속모→15, 10→25 뒷모도→26 뒷모개→22→27 사려→28 안찌→0.
 */
final class Board {

    enum Path { RING, A, B, C, D }

    static final int HOME = 0;
    static final int MO = 5;
    static final int BACK_MO = 10;
    static final int BANG = 22;

    /** 각 경로의 노드 순서. 마지막 원소가 참먹이(0)이고, 그 인덱스에 "정확히 서면" 아직 판 위. 그 너머로 가면 완주. */
    static final Map<Path, List<Integer>> PATHS = Map.of(
            Path.RING, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 0),
            Path.A, List.of(5, 20, 21, 22, 23, 24, 15, 16, 17, 18, 19, 0),
            Path.B, List.of(10, 25, 26, 22, 27, 28, 0),
            Path.C, List.of(22, 23, 24, 15, 16, 17, 18, 19, 0),
            Path.D, List.of(22, 27, 28, 0));

    static final String[] NAMES = {
            "참먹이", "도", "개", "걸", "윷", "모", "뒷도", "뒷개", "뒷걸", "뒷윷", "뒷모",
            "찌도", "찌개", "찌걸", "찌윷", "찌모", "날도", "날개", "날걸", "날윷",
            "앞모도", "앞모개", "방", "속윷", "속모", "뒷모도", "뒷모개", "사려", "안찌"};

    static int node(Path path, int index) {
        return PATHS.get(path).get(index);
    }

    static int lastIndex(Path path) {
        return PATHS.get(path).size() - 1;
    }

    private Board() {}
}
