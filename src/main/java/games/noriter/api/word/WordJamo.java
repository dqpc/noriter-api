package games.noriter.api.word;

/** 풀어쓰기 자모. 두벌식 키보드의 기본 자모 24개만 쓰고, 복합모음·쌍자음·겹받침은 이미 풀어진 상태로 온다. */
public final class WordJamo {

    public static final int LENGTH = 6;
    public static final String ALPHABET = "ㅂㅈㄷㄱㅅㅛㅕㅑㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ";

    public static boolean isValid(String jamo) {
        if (jamo == null || jamo.length() != LENGTH) return false;
        for (int i = 0; i < jamo.length(); i++) {
            if (ALPHABET.indexOf(jamo.charAt(i)) < 0) return false;
        }
        return true;
    }

    private WordJamo() {}
}
