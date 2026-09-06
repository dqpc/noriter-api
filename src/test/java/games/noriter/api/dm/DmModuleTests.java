package games.noriter.api.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.realtime.RealtimeService;
import games.noriter.api.support.Tables;
import games.noriter.api.user.UserService;
import games.noriter.api.user.UserSummary;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(extraIncludes = "config")
@ActiveProfiles("test")
class DmModuleTests {

    @Autowired DmService dm;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserService users;
    @MockitoBean RealtimeService realtime;

    @BeforeEach
    void setUp() {
        Tables.clearUsers(jdbc);
        Tables.insertUser(jdbc, 1, "goose");
        Tables.insertUser(jdbc, 2, "duck");
        Tables.insertUser(jdbc, 3, "stranger");
        Mockito.when(users.findById(Mockito.anyLong())).thenAnswer(i -> Optional.of(new UserSummary(i.getArgument(0), "u" + i.getArgument(0))));
        Mockito.when(users.findSummaries(Mockito.anyCollection())).thenAnswer(i -> {
            var out = new HashMap<Long, UserSummary>();
            for (Object id : (Collection<?>) i.getArgument(0)) out.put((Long) id, new UserSummary((Long) id, "u" + id));
            return out;
        });
        Mockito.when(users.isFriend(1L, 2L)).thenReturn(true);
    }

    @Test
    void openIsIdempotentAndRequiresFriendship() {
        var a = dm.open(1L, 2L);
        var b = dm.open(2L, 1L);
        assertThat(a.id()).isEqualTo(b.id());
        assertThat(b.otherNickname()).isEqualTo("u1");
        assertThatThrownBy(() -> dm.open(1L, 3L)).hasMessageContaining("친구");
        assertThatThrownBy(() -> dm.open(1L, 1L)).hasMessageContaining("자기 자신");
    }

    @Test
    void sendPushesToBothAndTracksUnreadPerMember() {
        var conv = dm.open(1L, 2L).id();
        dm.send(1L, conv, "안녕");
        dm.send(1L, conv, "뭐해");

        var pushed = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(realtime, Mockito.times(4)).send(Mockito.anyLong(), pushed.capture());
        assertThat(pushed.getAllValues()).allMatch(p -> p instanceof DmPushed);

        assertThat(dm.unreadTotal(2L)).isEqualTo(2);
        assertThat(dm.unreadTotal(1L)).isZero();
        assertThat(dm.list(2L)).singleElement().matches(c -> c.unread() == 2 && c.lastMessage().body().equals("뭐해"));

        var page = dm.messages(2L, conv, null);
        assertThat(page).extracting(MessageView::body).containsExactly("뭐해", "안녕");
        dm.markRead(2L, conv, page.get(0).id());
        assertThat(dm.unreadTotal(2L)).isZero();

        assertThatThrownBy(() -> dm.messages(3L, conv, null)).hasMessageContaining("없는 대화");
        assertThatThrownBy(() -> dm.send(1L, conv, "   ")).hasMessageContaining("내용");
    }

    @Test
    void paginatesByCursor() {
        var conv = dm.open(1L, 2L).id();
        for (int i = 0; i < 60; i++) dm.send(1L, conv, "m" + i);
        var first = dm.messages(1L, conv, null);
        assertThat(first).hasSize(50);
        assertThat(first.get(0).body()).isEqualTo("m59");
        var older = dm.messages(1L, conv, first.get(49).id());
        assertThat(older).hasSize(10);
        assertThat(older.get(9).body()).isEqualTo("m0");
    }
}
