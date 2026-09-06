package games.noriter.api.user.web.dto;

import games.noriter.api.user.Presence;
import games.noriter.api.user.UserProfile;
import java.time.Instant;

public record MeResponse(Long id, String nickname, String email, String characterId, Presence presence, Instant createdAt) {

    public static MeResponse from(UserProfile p) {
        return new MeResponse(p.id(), p.nickname(), p.email(), p.characterId(), p.presence(), p.createdAt());
    }
}
