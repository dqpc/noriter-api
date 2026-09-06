package games.noriter.api.dm;

import java.time.Instant;

public record MessageView(Long id, Long conversationId, Long senderId, String body, Instant createdAt) {}
