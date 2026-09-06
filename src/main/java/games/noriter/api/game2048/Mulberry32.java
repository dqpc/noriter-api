package games.noriter.api.game2048;

/** 웹 lib/random.ts 의 mulberry32 와 비트 단위로 같은 난수. 같은 seed 면 웹과 같은 타일이 나온다. */
final class Mulberry32 {

    private int a;

    Mulberry32(long seed) {
        this.a = (int) seed;
    }

    /** [0, 1) */
    double next() {
        a += 0x6d2b79f5;
        int t = a;
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + (t ^ (t >>> 7)) * (t | 61);
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }
}
