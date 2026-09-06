package games.noriter.api.score.web.dto;

import games.noriter.api.score.PlayFinished;

public record PlayFinishedResponse(long score, boolean adjusted) {

    public static PlayFinishedResponse from(PlayFinished p) {
        return new PlayFinishedResponse(p.score(), p.adjusted());
    }
}
