package games.noriter.api.word.web.dto;

import games.noriter.api.word.WordGuessOutcome;
import games.noriter.api.word.WordJudge;
import java.util.List;

/** 자리별 correct / present / absent. seq 는 계정 사용자의 몇 번째 추측인지(게스트 null) */
public record GuessResponse(List<String> statuses, Integer seq) {
    public static GuessResponse from(WordGuessOutcome o) {
        return new GuessResponse(o.statuses().stream().map(WordJudge.Status::json).toList(), o.seq());
    }
}
