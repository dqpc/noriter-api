package games.noriter.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.room.RoomFinished;
import games.noriter.api.room.RoomInvited;
import games.noriter.api.score.BestScoreUpdated;
import games.noriter.api.support.Tables;
import games.noriter.api.user.UserRegistered;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;

@ApplicationModuleTest(extraIncludes = "config")
@ActiveProfiles("test")
class NotificationModuleTests {

    @Autowired NotificationService notifications;
    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        Tables.clearUsers(jdbc);
        Tables.insertUser(jdbc, 1, "goose");
        Tables.insertUser(jdbc, 2, "duck");
    }

    @Test
    void eventsBecomeNotificationsOnlyForAccounts() {
        events.publishEvent(new UserRegistered(1L, "goose"));
        events.publishEvent(new RoomFinished("ab12", "yut", "윷놀이", true, true, List.of(
                new RoomFinished.Result("p1", 1L, "goose", 0, 2),
                new RoomFinished.Result("p2", null, "guest", 0, 1))));
        events.publishEvent(new BestScoreUpdated(1L, "2048", "2048", 4096, 2048));
        events.publishEvent(new RoomInvited("ab12", "yut", "윷놀이", 2L, "duck", 1L));

        var list = notifications.list(1L);
        assertThat(list).extracting(NotificationView::kind)
                .containsExactly(NotificationKind.INVITE, NotificationKind.BEST, NotificationKind.RESULT, NotificationKind.WELCOME);
        assertThat(list.get(0).link()).isEqualTo("/rooms/ab12");
        assertThat(list.get(2).title()).isEqualTo("윷놀이 2등");
        assertThat(notifications.unreadCount(1L)).isEqualTo(4);
        assertThat(notifications.list(2L)).isEmpty();
    }

    @Test
    void marksReadIndividuallyAndAll() {
        events.publishEvent(new UserRegistered(1L, "goose"));
        events.publishEvent(new BestScoreUpdated(1L, "2048", "2048", 4096, 2048));
        var first = notifications.list(1L).get(0).id();

        notifications.markRead(2L, first);
        assertThat(notifications.unreadCount(1L)).isEqualTo(2);
        notifications.markRead(1L, first);
        assertThat(notifications.unreadCount(1L)).isEqualTo(1);
        notifications.markAllRead(1L);
        assertThat(notifications.unreadCount(1L)).isZero();
        assertThat(notifications.list(1L)).allMatch(NotificationView::read);
    }
}
