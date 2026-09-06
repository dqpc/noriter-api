package games.noriter.api.score.web;

import games.noriter.api.game.UnknownGameException;
import games.noriter.api.score.PlayException;
import games.noriter.api.score.ScoreService;
import games.noriter.api.score.web.dto.FinishPlayRequest;
import games.noriter.api.score.web.dto.PlayFinishedResponse;
import games.noriter.api.score.web.dto.PlayStartedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 혼자 하기 세션. 시작 때 seed 를 받고, 끝날 때 점수(와 2048 입력 로그)를 보내면 서버가 검증해 확정한다. 게스트도 쓴다 */
@RestController
@RequestMapping("/api/games/{gameId}/plays")
@RequiredArgsConstructor
class PlayController {

    private final ScoreService scores;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlayStartedResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable String gameId) {
        return PlayStartedResponse.from(scores.startSolo(gameId, userId(jwt)));
    }

    @PostMapping("/{playId}/finish")
    PlayFinishedResponse finish(@AuthenticationPrincipal Jwt jwt, @PathVariable String gameId, @PathVariable String playId,
                                @RequestBody FinishPlayRequest req) {
        return PlayFinishedResponse.from(scores.finishSolo(gameId, playId, userId(jwt), req.score(), req.moves()));
    }

    private static Long userId(Jwt jwt) {
        return jwt == null ? null : Long.parseLong(jwt.getSubject());
    }

    @ExceptionHandler(PlayException.class)
    ResponseEntity<String> playError(PlayException e) {
        var status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_FINISHED -> HttpStatus.CONFLICT;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(e.getMessage());
    }

    @ExceptionHandler(UnknownGameException.class)
    ResponseEntity<String> unknownGame(UnknownGameException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
