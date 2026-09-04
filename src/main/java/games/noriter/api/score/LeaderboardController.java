package games.noriter.api.score;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 리더보드 조회. 점수 제출 API 는 인증 방식이 정해진 뒤 /api/scores 로 추가한다. */
@RestController
@RequestMapping("/api/public/leaderboard")
class LeaderboardController {

    private final ScoreService scores;

    LeaderboardController(ScoreService scores) {
        this.scores = scores;
    }

    @GetMapping("/{gameId}")
    List<LeaderboardEntry> leaderboard(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "20") int limit) {
        return scores.leaderboard(gameId, Math.min(Math.max(limit, 1), 100));
    }
}
