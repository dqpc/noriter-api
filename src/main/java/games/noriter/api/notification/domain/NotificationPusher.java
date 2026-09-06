package games.noriter.api.notification.domain;

import games.noriter.api.notification.NotificationView;

/** 접속 중인 사용자에게 새 알림을 바로 밀어 넣는다. 접속이 없으면 아무 일도 없다. */
public interface NotificationPusher {
    void push(Long userId, NotificationView view, long unread);
}
