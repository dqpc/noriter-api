package games.noriter.api.notification;

import java.time.Instant;

public record NotificationView(Long id, NotificationKind kind, String title, String body, String link, Instant createdAt, boolean read) {}
