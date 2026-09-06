package games.noriter.api.score;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.room.RoomFinished;
import games.noriter.api.support.Tables;
import games.noriter.api.user.UserService;
import games.noriter.api.user.UserSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(extraIncludes = {"game", "config"})
@ActiveProfiles("test")
class ScoreModuleTests {

    @Autowired ScoreService scores;
    @Autowired ApplicationEventPublisher events;
    @MockitoBean UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired games.noriter.api.score.infra.GamePlayRepository plays;

    @BeforeEach
    void setUp() {
        // game_score.user_id 는 DB 레벨 FK 라 유저 행이 있어야 한다 (user 모듈은 목).
        Tables.clearUsers(jdbc);
        Tables.insertUser(jdbc, 1, "goose");
        Mockito.when(users.findSummaries(Mockito.anyCollection())).thenReturn(Map.of(1L, new UserSummary(1L, "goose")));
    }

    @Test
    void submitPublishesEventAndAppearsOnLeaderboard(Scenario scenario) {
        scenario.stimulate(() -> scores.submit("2048", 1L, 4096))
                .andWaitForEventOfType(ScoreSubmitted.class)
                .matching(e -> e.score() == 4096)
                .toArrive();

        var board = scores.leaderboard("2048", 10);
        assertThat(board).hasSize(1);
        assertThat(board.get(0).nickname()).isEqualTo("goose");
        assertThat(board.get(0).rank()).isEqualTo(1);
    }

    @Test
    void roomFinishRecordsScoresForAccountsOnlyAndReportsNewBest(Scenario scenario) {
        events.publishEvent(finished("2048", false, List.of(result("p1", 1L, 2000, 2), result("p2", null, 3000, 1))));

        scenario.stimulate(() -> events.publishEvent(finished("2048", false, List.of(result("p1", 1L, 4000, 1)))))
                .andWaitForEventOfType(BestScoreUpdated.class)
                .matching(e -> e.score() == 4000 && e.previousBest() == 2000)
                .toArrive();

        var stats = scores.statsOf(1L);
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).plays()).isEqualTo(2);
        assertThat(stats.get(0).best()).isEqualTo(4000);
    }

    @Test
    void turnBasedGameStoresRankAndCountsWins() {
        events.publishEvent(finished("yut", true, List.of(result("p1", 1L, 0, 1))));
        events.publishEvent(finished("yut", true, List.of(result("p1", 1L, 0, 3))));

        var stats = scores.statsOf(1L);
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).turnBased()).isTrue();
        assertThat(stats.get(0).plays()).isEqualTo(2);
        assertThat(stats.get(0).wins()).isEqualTo(1);
        assertThat(stats.get(0).best()).isNull();
    }

    static RoomFinished finished(String gameId, boolean turnBased, List<RoomFinished.Result> results) {
        return new RoomFinished("ab12", gameId, gameId, turnBased, true, results);
    }

    static RoomFinished.Result result(String playerId, Long userId, long score, int rank) {
        return new RoomFinished.Result(playerId, userId, "n-" + playerId, score, rank);
    }

    @Test
    void recordsPlayForEveryParticipantIncludingGuestsAndSolo() {
        events.publishEvent(finished("yut", true, List.of(result("p1", 1L, 0, 1), result("p2", null, 0, 2))));
        scores.recordSolo("2048", null, 512L);

        assertThat(plays.findByGameIdOrderByCreatedAtAsc("yut")).hasSize(2)
                .allMatch(p -> p.getPlayerCount() == 2 && p.getPlayMode() == games.noriter.api.score.domain.GamePlay.Mode.ROOM);
        assertThat(plays.findByGameIdOrderByCreatedAtAsc("2048")).singleElement()
                .matches(p -> p.getUserId() == null && p.getPlayMode() == games.noriter.api.score.domain.GamePlay.Mode.SOLO);
        assertThat(scores.statsOf(1L)).hasSize(1);
    }
}
