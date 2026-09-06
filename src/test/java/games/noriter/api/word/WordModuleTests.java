package games.noriter.api.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.support.Tables;
import games.noriter.api.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 시계는 2026-09-08 KST 정오에 고정 → 오늘 3번, 어제 2번. 정답 픽스처는 1 입술, 2 한글, 3 마이크. */
@ApplicationModuleTest(extraIncludes = {"score", "game", "config"})
@ActiveProfiles("test")
class WordModuleTests {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-08T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired WordService words;
    @Autowired WordSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserService users;

    @BeforeEach
    void setUp() {
        Tables.clearUsers(jdbc);
        Tables.insertUser(jdbc, 1, "goose");
        seeder.seed();
    }

    @Test
    void todayIsNumberedFromEpochAndResetsAtKstMidnight() {
        var today = words.today();
        assertThat(today.number()).isEqualTo(3);
        assertThat(today.tries()).isEqualTo(6);
        assertThat(today.length()).isEqualTo(6);
        assertThat(today.resetAt()).isEqualTo(Instant.parse("2026-09-08T15:00:00Z"));
    }

    @Test
    void guessJudgesAgainstTodaysAnswerAndRejectsUnknownWords() {
        assertThat(words.guess(3, "ㅁㅏㅇㅣㅋㅡ")).containsOnly(WordJudge.Status.CORRECT);
        assertThat(words.guess(3, "ㅇㅣㅂㅅㅜㄹ")).containsExactly(
                WordJudge.Status.PRESENT, WordJudge.Status.PRESENT, WordJudge.Status.ABSENT,
                WordJudge.Status.ABSENT, WordJudge.Status.ABSENT, WordJudge.Status.ABSENT);
        assertThat(words.guess(2, "ㅎㅏㄴㄱㅡㄹ")).containsOnly(WordJudge.Status.CORRECT);

        assertThatThrownBy(() -> words.guess(3, "ㅎㅏㄴㄱㅡㄷ"))
                .isInstanceOf(WordException.class)
                .matches(e -> ((WordException) e).kind() == WordException.Kind.NOT_IN_DICTIONARY);
        assertThatThrownBy(() -> words.guess(3, "ㅎㅏㄴ")).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
        assertThatThrownBy(() -> words.guess(3, "ㅎㅏㄴㄱㅡㅐ")).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
        assertThatThrownBy(() -> words.guess(1, "ㅇㅣㅂㅅㅜㄹ")).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
    }

    @Test
    void pastAnswersOnlyForClosedPuzzles() {
        assertThat(words.pastAnswer(1).word()).isEqualTo("입술");
        assertThat(words.pastAnswer(2).word()).isEqualTo("한글");
        assertThatThrownBy(() -> words.pastAnswer(3)).matches(e -> ((WordException) e).kind() == WordException.Kind.NOT_FOUND);
        assertThat(words.isWord("ㅅㅓㄹㄴㅏㄹ")).isTrue();
        assertThat(words.isWord("ㅅㅓㄹㄴㅏㄷ")).isFalse();
    }

    @Test
    void guestFinishRevealsAnswerWithoutRecording() {
        var answer = words.finish(3, null, 4, false);
        assertThat(answer.word()).isEqualTo("마이크");
        assertThat(answer.meaning()).contains("소리");
        assertThat(jdbc.queryForObject("select count(*) from word_result", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from game_play", Long.class)).isZero();
    }

    private static final String WRONG = "ㅇㅣㅂㅎㅏㄱ"; // 입학: 어느 정답과도 다르다

    private void miss(int number, int times) {
        for (int i = 0; i < times; i++) words.guess(number, 1L, WRONG);
    }

    @Test
    void accountFinishRecordsOnceAndBuildsStats() {
        miss(2, 4);
        words.guess(2, 1L, "ㅎㅏㄴㄱㅡㄹ");
        words.finish(2, 1L, 1, false); // 클라이언트가 1이라 우겨도 서버는 5로 계산
        words.guess(3, 1L, "ㅅㅓㄹㄴㅏㄹ");
        words.guess(3, 1L, "ㅁㅏㅇㅣㅋㅡ");
        words.finish(3, 1L, null, true);
        words.finish(3, 1L, 1, false); // 재제출은 무시

        assertThat(jdbc.queryForObject("select count(*) from word_result", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select attempts from word_result where number = 3", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from game_play where game_id = 'word'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select max(score) from game_score where game_id = 'word'", Long.class)).isEqualTo(5);

        var stats = words.stats(1L);
        assertThat(stats.played()).isEqualTo(2);
        assertThat(stats.won()).isEqualTo(2);
        assertThat(stats.winRate()).isEqualTo(100);
        assertThat(stats.currentStreak()).isEqualTo(2);
        assertThat(stats.maxStreak()).isEqualTo(2);
        assertThat(stats.distribution()).isEqualTo(List.of(0, 1, 0, 0, 1, 0));
    }

    @Test
    void failureBreaksStreakButYesterdayKeepsCurrent() {
        jdbc.update("insert into word_result (user_id, number, attempts, hard, created_at) values (1, 1, 3, false, now())");
        miss(2, 6);
        words.finish(2, 1L, 2, false); // 여섯 번 다 틀렸으니 클라이언트 값과 무관하게 실패

        var afterFail = words.stats(1L);
        assertThat(afterFail.played()).isEqualTo(2);
        assertThat(afterFail.won()).isEqualTo(1);
        assertThat(afterFail.winRate()).isEqualTo(50);
        assertThat(afterFail.currentStreak()).isZero();
        assertThat(afterFail.maxStreak()).isEqualTo(1);

        Tables.clearUsers(jdbc);
        Tables.insertUser(jdbc, 1, "goose");
        miss(2, 5);
        words.guess(2, 1L, "ㅎㅏㄴㄱㅡㄹ");
        words.finish(2, 1L, null, false); // 어제만 맞혔고(여섯 번째에) 오늘은 아직
        var pending = words.stats(1L);
        assertThat(pending.currentStreak()).isEqualTo(1);
        assertThat(pending.distribution()).isEqualTo(List.of(0, 0, 0, 0, 0, 1));
    }

    @Test
    void finishValidatesAttemptsAndNumber() {
        assertThatThrownBy(() -> words.finish(3, 1L, 7, false)).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
        assertThatThrownBy(() -> words.finish(9, 1L, 1, false)).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
    }

    @Test
    void accountGuessesAreStoredAndCappedAtSix() {
        assertThatThrownBy(() -> words.finish(3, 1L, 3, false))
                .hasMessageContaining("끝나지 않은")
                .matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);

        for (int i = 1; i <= 6; i++) assertThat(words.guess(3, 1L, WRONG).seq()).isEqualTo(i);
        assertThatThrownBy(() -> words.guess(3, 1L, WRONG)).hasMessageContaining("여섯 번");
        assertThat(words.guesses(3, 1L)).hasSize(6);
        assertThat(words.guesses(3, 1L).getFirst().statuses()).containsExactly(
                WordJudge.Status.PRESENT, WordJudge.Status.PRESENT, WordJudge.Status.ABSENT,
                WordJudge.Status.ABSENT, WordJudge.Status.PRESENT, WordJudge.Status.ABSENT);

        words.finish(3, 1L, 1, false);
        assertThat(jdbc.queryForObject("select attempts from word_result where number = 3", Integer.class)).isNull();
        assertThatThrownBy(() -> words.guess(3, 1L, WRONG)).hasMessageContaining("이미 끝난");

        assertThat(words.guess(2, 1L, "ㅎㅏㄴㄱㅡㄹ").seq()).isEqualTo(1);
        assertThatThrownBy(() -> words.guess(2, 1L, WRONG)).hasMessageContaining("이미 맞힌");
        words.finish(2, 1L, 4, false);
        assertThat(jdbc.queryForObject("select attempts from word_result where number = 2", Integer.class)).isEqualTo(1);
    }

    @Test
    void guestGuessesAreNotStored() {
        for (int i = 0; i < 10; i++) assertThat(words.guess(3, null, WRONG).seq()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from word_guess", Long.class)).isZero();
        assertThat(words.finish(3, null, 2, false).word()).isEqualTo("마이크");
    }
}
