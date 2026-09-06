package games.noriter.api.score.web.dto;

import games.noriter.api.score.PlayStarted;

public record PlayStartedResponse(String playId, long seed) {

    public static PlayStartedResponse from(PlayStarted p) {
        return new PlayStartedResponse(p.playId(), p.seed());
    }
}
