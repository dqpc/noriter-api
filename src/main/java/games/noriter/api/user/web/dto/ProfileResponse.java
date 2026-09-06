package games.noriter.api.user.web.dto;

import games.noriter.api.user.PublicProfile;
import java.time.Instant;

public record ProfileResponse(Long id, String nickname, String characterId, Instant createdAt, PresenceResponse presence, boolean friend) {

    public static ProfileResponse from(PublicProfile p) {
        return new ProfileResponse(p.id(), p.nickname(), p.characterId(), p.createdAt(), PresenceResponse.from(p.presence()), p.friend());
    }
}
