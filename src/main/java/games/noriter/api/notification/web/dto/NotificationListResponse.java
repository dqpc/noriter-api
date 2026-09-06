package games.noriter.api.notification.web.dto;

import games.noriter.api.notification.NotificationKind;
import games.noriter.api.notification.NotificationView;
import java.time.Instant;
import java.util.List;

public record NotificationListResponse(long unread, List<Item> items) {

    public record Item(Long id, NotificationKind kind, String title, String body, String link, Instant createdAt, boolean read) {}

    public static NotificationListResponse from(long unread, List<NotificationView> views) {
        return new NotificationListResponse(unread, views.stream()
                .map(v -> new Item(v.id(), v.kind(), v.title(), v.body(), v.link(), v.createdAt(), v.read())).toList());
    }
}
