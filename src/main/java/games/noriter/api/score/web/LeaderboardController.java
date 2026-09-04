package games.noriter.api.score.web;

import games.noriter.api.score.ScoreService;
import games.noriter.api.score.web.dto.LeaderboardEntryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/leaderboard")
class LeaderboardController {

    private final ScoreService scores;

    LeaderboardController(ScoreService scores) {
        this.scores = scores;
    }

    @GetMapping
    List<LeaderboardEntryResponse> leaderboard(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "20") int limit) {
        return scores.leaderboard(gameId, Math.min(Math.max(limit, 1), 100)).stream()
                .map(LeaderboardEntryResponse::from)
                .toList();
    }
}
