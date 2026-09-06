package games.noriter.api.user.web.dto;

import games.noriter.api.user.FriendView;

public record FriendResponse(Long id, String nickname, String characterId, PresenceResponse presence) {

    public static FriendResponse from(FriendView f) {
        return new FriendResponse(f.id(), f.nickname(), f.characterId(), PresenceResponse.from(f.presence()));
    }
}
