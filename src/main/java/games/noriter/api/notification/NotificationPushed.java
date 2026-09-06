package games.noriter.api.notification;

/** 개인 채널로 나가는 새 알림 메시지 */
public record NotificationPushed(String type, NotificationView item, long unread) {
    public NotificationPushed(NotificationView item, long unread) { this("notification", item, unread); }
}
