package games.noriter.api.score;

import games.noriter.api.score.domain.GameScore;
import games.noriter.api.score.infra.GameScoreRepository;
import games.noriter.api.user.UserService;
import games.noriter.api.user.UserSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final GameScoreRepository scores;
    private final UserService users;
    private final ApplicationEventPublisher events;

    @Transactional
    public void submit(String gameId, Long userId, long score) {
        scores.save(new GameScore(gameId, userId, score));
        events.publishEvent(new ScoreSubmitted(gameId, userId, score));
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
}
