package games.noriter.api.word;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Wordle 판정. 자리까지 맞는 것을 먼저 확정하고, 남은 정답 자모 개수만큼만 '있음'을 준다. */
public final class WordJudge {

    public enum Status {
        CORRECT, PRESENT, ABSENT;

        public String json() {
            return name().toLowerCase();
        }
    }

    public static List<Status> judge(String answer, String guess) {
        if (answer.length() != guess.length()) throw new IllegalArgumentException("length mismatch");
        var result = new Status[guess.length()];
        Arrays.fill(result, Status.ABSENT);
        Map<Character, Integer> remaining = new HashMap<>();
        for (int i = 0; i < guess.length(); i++) {
            if (guess.charAt(i) == answer.charAt(i)) result[i] = Status.CORRECT;
            else remaining.merge(answer.charAt(i), 1, Integer::sum);
        }
        for (int i = 0; i < guess.length(); i++) {
            if (result[i] == Status.CORRECT) continue;
            int left = remaining.getOrDefault(guess.charAt(i), 0);
            if (left > 0) {
                result[i] = Status.PRESENT;
                remaining.put(guess.charAt(i), left - 1);
            }
        }
        return new ArrayList<>(Arrays.asList(result));
    }

    private WordJudge() {}
}
