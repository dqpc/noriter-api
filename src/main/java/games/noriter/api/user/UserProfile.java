package games.noriter.api.user;

import java.time.Instant;

public record UserProfile(Long id, String nickname, String email, String characterId, Presence presence, Instant createdAt) {}
