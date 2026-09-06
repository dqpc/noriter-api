package games.noriter.api.notification;

import games.noriter.api.notification.domain.Notification;
import games.noriter.api.notification.infra.NotificationRepository;
import games.noriter.api.realtime.RealtimeService;
import games.noriter.api.room.RoomFinished;
import games.noriter.api.room.RoomInvited;
import games.noriter.api.score.BestScoreUpdated;
import games.noriter.api.user.UserRegistered;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int PAGE = 50;

    private final NotificationRepository notifications;
    private final RealtimeService realtime;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NotificationView> list(Long userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, PAGE)).stream().map(Notification::toView).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        var now = Instant.now(clock);
        notifications.findByUserIdAndReadAtIsNull(userId).forEach(n -> n.markRead(now));
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notifications.findById(notificationId).filter(n -> n.getUserId().equals(userId)).ifPresent(n -> n.markRead(Instant.now(clock)));
    }

    @EventListener
    @Transactional
    public void onRegistered(UserRegistered e) {
        push(e.userId(), NotificationKind.WELCOME, "놀이터에 온 걸 환영해요", e.nickname() + " 님, 대기실에서 다른 사람의 프로필을 열어 친구로 추가해 보세요", null);
    }

    @EventListener
    @Transactional
    public void onRoomFinished(RoomFinished e) {
        int total = e.results().size();
        for (var r : e.results()) {
            if (r.userId() == null || r.rank() == null) continue;
            var title = e.gameName() + " " + r.rank() + "등";
            var body = e.turnBased()
                    ? total + "명 중 " + r.rank() + "등 · 방 " + e.roomId()
                    : total + "명 중 " + r.rank() + "등 · " + r.score() + "점 · 방 " + e.roomId();
            push(r.userId(), NotificationKind.RESULT, title, body, null);
        }
    }

    @EventListener
    @Transactional
    public void onBestScore(BestScoreUpdated e) {
        push(e.userId(), NotificationKind.BEST, e.gameName() + " 최고 기록 갱신!", e.previousBest() + "점 → " + e.score() + "점", null);
    }

    @EventListener
    @Transactional
    public void onInvited(RoomInvited e) {
        push(e.toUserId(), NotificationKind.INVITE, e.fromNickname() + " 님의 초대", e.gameName() + " 방에 초대했어요 · 방 " + e.roomId(), "/rooms/" + e.roomId());
    }

    private void push(Long userId, NotificationKind kind, String title, String body, String link) {
        var saved = notifications.save(new Notification(userId, kind, title, body, link, Instant.now(clock)));
        realtime.send(userId, new NotificationPushed(saved.toView(), notifications.countByUserIdAndReadAtIsNull(userId)));
    }
}
