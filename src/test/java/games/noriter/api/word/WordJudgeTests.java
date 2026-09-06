package games.noriter.api.word;

import static games.noriter.api.word.WordJudge.Status.ABSENT;
import static games.noriter.api.word.WordJudge.Status.CORRECT;
import static games.noriter.api.word.WordJudge.Status.PRESENT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WordJudgeTests {

    @Test
    void allCorrectAndAllAbsent() {
        assertThat(WordJudge.judge("ㅇㅣㅂㅅㅜㄹ", "ㅇㅣㅂㅅㅜㄹ")).containsOnly(CORRECT);
        assertThat(WordJudge.judge("ㅇㅣㅂㅅㅜㄹ", "ㅎㅏㄴㄱㅡㄷ")).containsOnly(ABSENT);
    }

    @Test
    void presentOnlyAsManyTimesAsInAnswer() {
        // 정답에 ㅇ 이 하나뿐이면 추측의 둘째 ㅇ 은 없음
        assertThat(WordJudge.judge("ㅇㅣㅂㅅㅜㄹ", "ㅅㅇㅇㅏㅣㅁ"))
                .containsExactly(PRESENT, PRESENT, ABSENT, ABSENT, PRESENT, ABSENT);
    }

    @Test
    void correctConsumesBeforePresent() {
        // 정답의 ㅏ 는 하나. 넷째 자리에서 맞혔으니 둘째 ㅏ 는 없음
        assertThat(WordJudge.judge("ㄷㅓㄴㅏㅁㅜ", "ㅁㅏㄱㅏㅅㅜ"))
                .containsExactly(PRESENT, ABSENT, ABSENT, CORRECT, ABSENT, CORRECT);
    }

    @Test
    void duplicatesInAnswerAllowThatManyPresents() {
        // 정답에 ㅏ 셋. 하나는 자리까지 맞고 나머지 둘은 자리가 틀림
        assertThat(WordJudge.judge("ㄴㅏㄹㅏㅁㅏ", "ㅏㅏㅏㅇㅇㅇ"))
                .containsExactly(PRESENT, CORRECT, PRESENT, ABSENT, ABSENT, ABSENT);
    }
}
