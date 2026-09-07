package games.noriter.api.wall;

import java.time.Instant;

public record WallPostView(Long id, String nickname, boolean guest, String characterId, String content,
                           Instant createdAt, Instant updatedAt, boolean mine) {}
