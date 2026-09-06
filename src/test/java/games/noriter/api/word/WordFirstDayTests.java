package games.noriter.api.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.support.Tables;
import games.noriter.api.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** 첫날(2026-09-06, 1번)에는 "어제"가 없다. 0번은 열려 있으면 안 된다 (2026-09-06 dev 에서 404 로 새던 버그). */
@ApplicationModuleTest(extraIncludes = {"score", "game", "config"})
@ActiveProfiles("test")
class WordFirstDayTests {

    @TestConfiguration
    static class FirstDayClock {
        @Bean
        @Primary
        Clock firstDayClock() {
            return Clock.fixed(Instant.parse("2026-09-06T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired WordService words;
    @Autowired WordSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserService users;

    @BeforeEach
    void setUp() {
        Tables.clearUsers(jdbc);
        seeder.seed();
    }

    @Test
    void numberZeroIsNotOpenOnTheFirstDay() {
        assertThat(words.today().number()).isEqualTo(1);
        assertThat(words.guess(1, "ㅇㅣㅂㅅㅜㄹ")).containsOnly(WordJudge.Status.CORRECT);
        assertThatThrownBy(() -> words.guess(0, "ㅇㅣㅂㅅㅜㄹ")).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
        assertThatThrownBy(() -> words.guess(-1, "ㅇㅣㅂㅅㅜㄹ")).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
        assertThatThrownBy(() -> words.finish(0, null, 3, false)).matches(e -> ((WordException) e).kind() == WordException.Kind.INVALID);
        assertThatThrownBy(() -> words.pastAnswer(0)).matches(e -> ((WordException) e).kind() == WordException.Kind.NOT_FOUND);
    }
}
