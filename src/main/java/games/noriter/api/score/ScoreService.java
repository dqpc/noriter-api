package games.noriter.api.score;

import games.noriter.api.game.GameCatalog;
import games.noriter.api.game.GameSpec;
import games.noriter.api.room.RoomFinished;
import games.noriter.api.score.domain.GamePlay;
import games.noriter.api.score.domain.GameScore;
import games.noriter.api.score.infra.GamePlayRepository;
import games.noriter.api.score.infra.GameScoreRepository;
import games.noriter.api.user.UserService;
import games.noriter.api.user.UserSummary;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    /** 지연·재접속으로 늦게 도착한 종료를 봐주는 여유 (방의 SCORE_SLACK 과 같은 값) */
    static final Duration SOLO_SLACK = Duration.ofSeconds(2);

    private final SecureRandom random = new SecureRandom();
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

    /** 혼자 하기 한 판을 결과만으로 기록(서버가 판정하는 글딱지용). 게스트는 이용 기록만, 계정은 점수 기록도 남는다. */
    @Transactional
    public void recordSolo(String gameId, Long userId, Long score) {
        var spec = games.require(gameId);
        plays.save(new GamePlay(gameId, GamePlay.Mode.SOLO, null, userId, 1, score, null, Instant.now(clock)));
        if (userId == null || score == null || spec.turnBased()) return;
        recordBest(spec, userId, score);
    }

    /** 혼자 하기 시작. 서버가 seed 를 정해 주고 세션 행을 먼저 남긴다 */
    @Transactional
    public PlayStarted startSolo(String gameId, Long userId) {
        var spec = games.require(gameId);
        if (spec.turnBased()) throw new PlayException(PlayException.Kind.INVALID, "turn-based game has no solo play");
        long seed = random.nextInt(Integer.MAX_VALUE - 1) + 1;
        var play = plays.save(GamePlay.startSolo(gameId, userId, newToken(), seed, Instant.now(clock)));
        return new PlayStarted(play.getToken(), seed);
    }

    /**
     * 혼자 하기 종료. 입력 로그가 오면 seed 로 재생한 점수를, 아니면 경과 시간 대비 상한(GameSpec.scoreLimits)을 넘지 않는 점수를 확정한다.
     * 계정의 판은 본인만 끝낼 수 있고, 게스트 판은 토큰을 아는 쪽이 끝낸다.
     */
    @Transactional
    public PlayFinished finishSolo(String gameId, String playId, Long userId, Long score, String moves) {
        var play = plays.findByToken(playId == null ? "" : playId)
                .filter(p -> p.getGameId().equals(gameId))
                .filter(p -> p.getUserId() == null || p.getUserId().equals(userId))
                .orElseThrow(() -> new PlayException(PlayException.Kind.NOT_FOUND, "play not found"));
        if (play.isFinished()) throw new PlayException(PlayException.Kind.ALREADY_FINISHED, "play already finished");
        if (score == null || score < 0) throw new PlayException(PlayException.Kind.INVALID, "score required");
        var spec = games.require(gameId);
        var now = Instant.now(clock);
        long accepted = verifiedSolo(spec, play, score, moves, now);
        play.finish(accepted, now);
        if (play.getUserId() != null) recordBest(spec, play.getUserId(), accepted);
        return new PlayFinished(accepted, accepted != score);
    }

    private long verifiedSolo(GameSpec spec, GamePlay play, long score, String moves, Instant now) {
        var replayer = games.replayer(spec.id());
        if (replayer.isPresent() && moves != null) {
            // 혼자 하기는 목표 타일을 클라이언트가 고르지만, 목표에 닿으면 입력이 더 안 쌓이므로 기본 옵션으로 재생해도 점수는 같다
            var replay = replayer.get().replay(play.getSeed(), spec.defaultOptions(), moves);
            if (replay.score() != score || !replay.complete()) {
                log.warn("solo score replayed game={} play={} client={} replayed={} moves={}/{}",
                        spec.id(), play.getId(), score, replay.score(), replay.applied(), moves.length());
            }
            return replay.score();
        }
        var limits = spec.scoreLimits();
        if (limits == null) return score;
        long elapsed = Math.max(0, Duration.between(play.getCreatedAt(), now).toSeconds());
        long allowed = Math.min(limits.maxScore(), limits.maxPerSecond() * (elapsed + SOLO_SLACK.toSeconds()));
        if (score <= allowed) return score;
        log.warn("solo score capped game={} play={} client={} allowed={} elapsedSec={}", spec.id(), play.getId(), score, allowed, elapsed);
        return allowed;
    }

    private void recordBest(GameSpec spec, Long userId, long score) {
        var previous = best(userId, spec.id(), spec.higherIsBetter());
        submit(spec.id(), userId, score);
        if (previous.isPresent() && (spec.higherIsBetter() ? score > previous.get() : score < previous.get())) {
            events.publishEvent(new BestScoreUpdated(userId, spec.id(), spec.name(), score, previous.get()));
        }
    }

    private String newToken() {
        var bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
