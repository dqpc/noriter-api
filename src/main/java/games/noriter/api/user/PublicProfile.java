package games.noriter.api.user;

import java.time.Instant;

public record PublicProfile(Long id, String nickname, String characterId, Instant createdAt, PresenceView presence, boolean friend) {}
