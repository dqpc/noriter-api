package games.noriter.api.score.web;

import games.noriter.api.score.ScoreService;
import games.noriter.api.score.web.dto.UserGameStatsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/scores")
@RequiredArgsConstructor
class UserScoreController {

    private final ScoreService scores;

    @GetMapping
    List<UserGameStatsResponse> stats(@PathVariable Long userId) {
        return scores.statsOf(userId).stream().map(UserGameStatsResponse::from).toList();
    }
}
