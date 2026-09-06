package games.noriter.api.word;

import java.util.List;

/** 추측 판정. seq 는 계정 사용자의 몇 번째 추측인지, 게스트는 null. */
public record WordGuessOutcome(List<WordJudge.Status> statuses, Integer seq) {}
