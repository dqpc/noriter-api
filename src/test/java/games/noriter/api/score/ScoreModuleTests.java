package games.noriter.api.score;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.Mockito;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

@ApplicationModuleTest
@ActiveProfiles("test")
class ScoreModuleTests {

    @Autowired ScoreService scores;
    @MockitoBean UserService users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void submitPublishesEventAndAppearsOnLeaderboard(Scenario scenario) {
        // game_score.user_id 는 DB 레벨 FK 라 유저 행이 있어야 한다 (user 모듈은 목).
        jdbc.update("insert into app_user (id, provider, provider_id, nickname, created_at) values (1, 'test', 't-1', 'goose', now())");
        Mockito.when(users.findSummaries(Mockito.anyCollection()))
                .thenReturn(Map.of(1L, new games.noriter.api.user.UserSummary(1L, "goose")));

        scenario.stimulate(() -> scores.submit("2048", 1L, 4096))
                .andWaitForEventOfType(ScoreSubmitted.class)
                .matching(e -> e.score() == 4096)
                .toArrive();

        var board = scores.leaderboard("2048", 10);
        assertThat(board).hasSize(1);
        assertThat(board.get(0).nickname()).isEqualTo("goose");
        assertThat(board.get(0).rank()).isEqualTo(1);
    }
}
