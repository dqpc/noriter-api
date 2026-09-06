package games.noriter.api.notification.web.dto;

import games.noriter.api.notification.NotificationView;

public sealed interface MeServerMessage {

    record Hello(String type, long unread) implements MeServerMessage {
        public Hello(long unread) { this("hello", unread); }
    }

    record Pushed(String type, NotificationListResponse.Item item, long unread) implements MeServerMessage {
        public Pushed(NotificationView v, long unread) {
            this("notification", new NotificationListResponse.Item(v.id(), v.kind(), v.title(), v.body(), v.link(), v.createdAt(), v.read()), unread);
        }
    }

    record Pong(String type) implements MeServerMessage {
        public Pong() { this("pong"); }
    }
}
