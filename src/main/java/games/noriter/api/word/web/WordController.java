package games.noriter.api.word.web;

import games.noriter.api.word.WordException;
import games.noriter.api.word.WordJudge;
import games.noriter.api.word.WordService;
import games.noriter.api.word.web.dto.AnswerResponse;
import games.noriter.api.word.web.dto.DictionaryResponse;
import games.noriter.api.word.web.dto.ErrorResponse;
import games.noriter.api.word.web.dto.GuessRequest;
import games.noriter.api.word.web.dto.GuessResponse;
import games.noriter.api.word.web.dto.ResultRequest;
import games.noriter.api.word.web.dto.ResultResponse;
import games.noriter.api.word.web.dto.StatsResponse;
import games.noriter.api.word.web.dto.TodayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/word")
@RequiredArgsConstructor
class WordController {

    private final WordService words;

    private static Long userId(Jwt jwt) {
        return jwt == null ? null : Long.parseLong(jwt.getSubject());
    }

    @GetMapping("/today")
    TodayResponse today() {
        return TodayResponse.from(words.today());
    }

    @PostMapping("/guesses")
    GuessResponse guess(@RequestBody @Validated GuessRequest req) {
        return new GuessResponse(words.guess(req.number(), req.jamo()).stream().map(WordJudge.Status::json).toList());
    }

    /** 게스트는 정답만 받고, 계정은 결과가 저장되어 전적도 같이 온다 */
    @PostMapping("/results")
    ResultResponse finish(@AuthenticationPrincipal Jwt jwt, @RequestBody @Validated ResultRequest req) {
        var me = userId(jwt);
        var answer = words.finish(req.number(), me, req.attempts(), Boolean.TRUE.equals(req.hard()));
        return new ResultResponse(AnswerResponse.from(answer), me == null ? null : StatsResponse.from(words.stats(me)));
    }

    @GetMapping("/stats")
    StatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        var me = userId(jwt);
        if (me == null) throw new WordException(WordException.Kind.UNAUTHORIZED, "로그인이 필요합니다");
        return StatsResponse.from(words.stats(me));
    }

    @GetMapping("/dictionary/{jamo}")
    DictionaryResponse dictionary(@PathVariable String jamo) {
        return new DictionaryResponse(words.isWord(jamo));
    }

    @GetMapping("/answers/{number}")
    AnswerResponse answer(@PathVariable int number) {
        return AnswerResponse.from(words.pastAnswer(number));
    }

    @ExceptionHandler(WordException.class)
    ResponseEntity<ErrorResponse> onWordException(WordException e) {
        var status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_IN_DICTIONARY -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(e.kind().name(), e.getMessage()));
    }
}
