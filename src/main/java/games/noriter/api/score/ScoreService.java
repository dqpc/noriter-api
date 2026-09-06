package games.noriter.api.score;

import games.noriter.api.game.GameCatalog;
import games.noriter.api.room.RoomFinished;
import games.noriter.api.score.domain.GamePlay;
import games.noriter.api.score.domain.GameScore;
import games.noriter.api.score.infra.GamePlayRepository;
import games.noriter.api.score.infra.GameScoreRepository;
import games.noriter.api.user.UserService;
import games.noriter.api.user.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final GameScoreRepository scores;
    private final GamePlayRepository plays;
    private final UserService users;
    private final GameCatalog games;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void submit(String gameId, Long userId, long score) {
        scores.save(new GameScore(gameId, userId, score));
        events.publishEvent(new ScoreSubmitted(gameId, userId, score));
    }

    /** 혼자 하기 한 판. 게스트는 userId 없이 이용 기록만 남는다. */
    @Transactional
    public void recordSolo(String gameId, Long userId, Long score) {
        games.require(gameId);
        plays.save(new GamePlay(gameId, GamePlay.Mode.SOLO, null, userId, 1, score, null, Instant.now(clock)));
    }

    /** 방 한 판이 끝나면 참가자 전원의 이용 기록을, 로그인한 참가자는 점수 기록도 남긴다. 턴제는 점수 대신 순위를 저장한다. */
    @EventListener
    @Transactional
    public void onRoomFinished(RoomFinished e) {
        var now = Instant.now(clock);
        for (var r : e.results()) {
            plays.save(new GamePlay(e.gameId(), GamePlay.Mode.ROOM, e.roomId(), r.userId(), e.results().size(), r.score(), r.rank(), now));
            if (r.userId() == null) continue;
            long value = e.turnBased() ? (r.rank() == null ? 0 : r.rank()) : r.score();
            var previous = e.turnBased() ? Optional.<Long>empty() : best(r.userId(), e.gameId(), e.higherIsBetter());
            submit(e.gameId(), r.userId(), value);
            if (previous.isPresent() && (e.higherIsBetter() ? value > previous.get() : value < previous.get())) {
                events.publishEvent(new BestScoreUpdated(r.userId(), e.gameId(), e.gameName(), value, previous.get()));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(String gameId, int limit) {
        var top = scores.findByGameIdOrderByScoreDescCreatedAtAsc(gameId, PageRequest.of(0, limit));
        Map<Long, UserSummary> names = users.findSummaries(top.stream().map(GameScore::getUserId).toList());
        var out = new ArrayList<LeaderboardEntry>(top.size());
        for (int i = 0; i < top.size(); i++) {
            var s = top.get(i);
            var name = names.get(s.getUserId());
            out.add(new LeaderboardEntry(i + 1, s.getUserId(), name == null ? "?" : name.nickname(), s.getScore()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<UserGameStats> statsOf(Long userId) {
        Map<String, List<GameScore>> byGame = new LinkedHashMap<>();
        for (var s : scores.findByUserIdOrderByCreatedAtAsc(userId)) byGame.computeIfAbsent(s.getGameId(), k -> new ArrayList<>()).add(s);
        var out = new ArrayList<UserGameStats>();
        byGame.forEach((gameId, list) -> {
            var spec = games.find(gameId);
            boolean turnBased = spec.map(g -> g.turnBased()).orElse(false);
            boolean higher = spec.map(g -> g.higherIsBetter()).orElse(true);
            String name = spec.map(g -> g.name()).orElse(gameId);
            Long best = turnBased ? null : list.stream().map(GameScore::getScore)
                    .max(higher ? Comparator.naturalOrder() : Comparator.reverseOrder()).orElse(null);
            long wins = turnBased ? list.stream().filter(s -> s.getScore() == 1).count() : 0;
            out.add(new UserGameStats(gameId, name, turnBased, list.size(), best, wins));
        });
        return out;
    }

    private Optional<Long> best(Long userId, String gameId, boolean higherIsBetter) {
        return scores.findByUserIdAndGameId(userId, gameId).stream().map(GameScore::getScore)
                .max(higherIsBetter ? Comparator.naturalOrder() : Comparator.reverseOrder());
    }
}
