package games.noriter.api.score.web;

import games.noriter.api.score.ScoreService;
import games.noriter.api.score.web.dto.PlayRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 혼자 하기 한 판이 끝났을 때. 게스트도 보낸다 (이용 통계용) */
@RestController
@RequestMapping("/api/games/{gameId}/plays")
@RequiredArgsConstructor
class PlayController {

    private final ScoreService scores;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void record(@AuthenticationPrincipal Jwt jwt, @PathVariable String gameId, @RequestBody PlayRequest req) {
        scores.recordSolo(gameId, jwt == null ? null : Long.parseLong(jwt.getSubject()), req.score());
    }
}
