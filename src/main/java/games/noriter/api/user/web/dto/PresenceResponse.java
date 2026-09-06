package games.noriter.api.user.web.dto;

import games.noriter.api.user.Activity;
import games.noriter.api.user.PresenceView;

public record PresenceResponse(PresenceView.State state, Activity activity, String gameId, String roomId) {

    public static PresenceResponse from(PresenceView p) {
        return new PresenceResponse(p.state(), p.activity(), p.gameId(), p.roomId());
    }
}
