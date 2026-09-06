package games.noriter.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@ApplicationModuleTest(extraIncludes = {"game", "game2048", "config"})
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
    void soloSessionStartsWithSeedAndFinishesWithinLimits() {
        var started = scores.startSolo("stairs", 1L);
        assertThat(started.playId()).hasSizeGreaterThan(15);
        assertThat(started.seed()).isPositive();

        var finished = scores.finishSolo("stairs", started.playId(), 1L, 30L, null);
        assertThat(finished.score()).isEqualTo(30);
        assertThat(finished.adjusted()).isFalse();
        assertThat(scores.statsOf(1L)).singleElement().matches(s -> s.gameId().equals("stairs") && s.best() == 30);
        var play = plays.findByToken(started.playId()).orElseThrow();
        assertThat(play.isFinished()).isTrue();
        assertThat(play.getScore()).isEqualTo(30);
    }

    @Test
    void soloScoreBeyondElapsedTimeIsCappedToAllowedMaximum() {
        var started = scores.startSolo("stairs", null);
        // 시작 직후(경과 0초 + 여유 2초)에 계단 500칸은 불가능: 초당 40칸 × 2초 = 80 으로 깎인다
        var finished = scores.finishSolo("stairs", started.playId(), null, 500L, null);
        assertThat(finished.score()).isEqualTo(80);
        assertThat(finished.adjusted()).isTrue();
        assertThat(plays.findByToken(started.playId()).orElseThrow().getScore()).isEqualTo(80);
    }

    @Test
    void solo2048ReplaysMovesAndOverridesClientScore() {
        var started = scores.startSolo("2048", 1L);
        var board = new games.noriter.api.game2048.Board2048(started.seed(), 2048);
        var moves = new StringBuilder();
        for (int i = 0; i < 40 && !board.ended(); i++) {
            for (char code : "0123".toCharArray()) {
                if (board.step(code)) {
                    moves.append(code);
                    break;
                }
            }
        }
        assertThat(board.score()).isPositive();

        var finished = scores.finishSolo("2048", started.playId(), 1L, 999_999L, moves.toString());
        assertThat(finished.score()).isEqualTo(board.score());
        assertThat(finished.adjusted()).isTrue();
        assertThat(scores.leaderboard("2048", 10)).singleElement().matches(e -> e.score() == board.score());
    }

    @Test
    void soloFinishRejectsUnknownForeignOrRepeatedPlays() {
        assertThatThrownBy(() -> scores.finishSolo("2048", "nope", null, 10L, null))
                .isInstanceOf(PlayException.class).matches(e -> ((PlayException) e).kind() == PlayException.Kind.NOT_FOUND);

        var mine = scores.startSolo("2048", 1L);
        assertThatThrownBy(() -> scores.finishSolo("2048", mine.playId(), null, 10L, null))
                .isInstanceOf(PlayException.class).matches(e -> ((PlayException) e).kind() == PlayException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> scores.finishSolo("stairs", mine.playId(), 1L, 10L, null))
                .isInstanceOf(PlayException.class).matches(e -> ((PlayException) e).kind() == PlayException.Kind.NOT_FOUND);

        scores.finishSolo("2048", mine.playId(), 1L, 10L, null);
        assertThatThrownBy(() -> scores.finishSolo("2048", mine.playId(), 1L, 20L, null))
                .isInstanceOf(PlayException.class).matches(e -> ((PlayException) e).kind() == PlayException.Kind.ALREADY_FINISHED);

        assertThatThrownBy(() -> scores.startSolo("yut", 1L))
                .isInstanceOf(PlayException.class).matches(e -> ((PlayException) e).kind() == PlayException.Kind.INVALID);
    }

    @Test
    void recordsPlayForEveryParticipantIncludingGuestsAndSolo() {
        events.publishEvent(finished("yut", true, List.of(result("p1", 1L, 0, 1), result("p2", null, 0, 2))));
        scores.recordSolo("2048", null, 512L);
        scores.recordSolo("2048", 1L, 1024L);

        assertThat(plays.findByGameIdOrderByCreatedAtAsc("yut")).hasSize(2)
                .allMatch(p -> p.getPlayerCount() == 2 && p.getPlayMode() == games.noriter.api.score.domain.GamePlay.Mode.ROOM);
        assertThat(plays.findByGameIdOrderByCreatedAtAsc("2048")).hasSize(2)
                .allMatch(p -> p.getPlayMode() == games.noriter.api.score.domain.GamePlay.Mode.SOLO);
        assertThat(scores.statsOf(1L)).extracting(UserGameStats::gameId).containsExactlyInAnyOrder("yut", "2048");
        assertThat(scores.statsOf(1L)).filteredOn(s -> s.gameId().equals("2048")).singleElement()
                .matches(s -> s.best() == 1024 && s.plays() == 1);
        assertThat(scores.leaderboard("2048", 10)).singleElement().matches(e -> e.score() == 1024);
    }
}
