package games.noriter.api.game.yut;

enum Throw {
    BACKDO(-1, "빽도"), DO(1, "도"), GAE(2, "개"), GEOL(3, "걸"), YUT(4, "윷"), MO(5, "모");

    final int steps;
    final String label;

    Throw(int steps, String label) {
        this.steps = steps;
        this.label = label;
    }

    boolean again() {
        return this == YUT || this == MO;
    }

    /** sticks[i] = true 면 배(평평한 면)가 위. sticks[0] 이 빽도 표시 막대. */
    static Throw of(boolean[] sticks, boolean backdoEnabled) {
        int flats = 0;
        for (boolean s : sticks) if (s) flats++;
        if (flats == 0) return MO;
        if (flats == 1 && backdoEnabled && sticks[0]) return BACKDO;
        return switch (flats) {
            case 1 -> DO;
            case 2 -> GAE;
            case 3 -> GEOL;
            default -> YUT;
        };
    }
}
