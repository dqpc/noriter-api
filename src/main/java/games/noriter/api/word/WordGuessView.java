package games.noriter.api.word;

import java.util.List;

/** 저장된 추측 한 줄. 새로고침 뒤 판을 복원할 때 내려준다. */
public record WordGuessView(String jamo, List<WordJudge.Status> statuses) {}
