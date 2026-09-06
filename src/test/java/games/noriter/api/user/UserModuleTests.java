package games.noriter.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.support.Tables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;

@ApplicationModuleTest(extraIncludes = "config")
@ActiveProfiles("test")
class UserModuleTests {

    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        Tables.clearUsers(jdbc);
    }

    UserProfile register(String nickname) {
        return users.register(nickname, "pass1234", null, "tiger").user();
    }

    @Test
    void registersThenLogsInWithSameNicknameIgnoringCase(Scenario scenario) {
        scenario.stimulate(() -> users.register("Goose", "pass1234", "g@example.com", "tiger"))
                .andWaitForEventOfType(UserRegistered.class)
                .matching(e -> e.nickname().equals("Goose"))
                .toArrive();

        var login = users.login("goose", "pass1234");
        assertThat(login.user().nickname()).isEqualTo("Goose");
        assertThat(login.user().characterId()).isEqualTo("tiger");
        assertThat(users.authenticate(login.token())).map(UserProfile::nickname).contains("Goose");
        assertThat(users.findByNickname("GOOSE")).isPresent();
        assertThat(users.findByNickname("nobody")).isEmpty();
    }

    @Test
    void rejectsBadNicknameDuplicateAndWrongPassword() {
        register("goose");

        assertThatThrownBy(() -> users.register("g", "pass1234", null, null)).hasMessageContaining("2~12");
        assertThatThrownBy(() -> users.register("GOOSE", "pass1234", null, null)).hasMessageContaining("이미 있는");
        assertThatThrownBy(() -> users.login("goose", "wrong")).hasMessageContaining("비밀번호");
        assertThat(users.authenticate("not-a-token")).isEmpty();
    }

    @Test
    void friendsAreOneWayAndShowPresence() {
        var me = register("me");
        var other = register("other");

        users.addFriend(me.id(), other.id());
        users.addFriend(me.id(), other.id());
        assertThat(users.friends(me.id())).extracting(FriendView::nickname).containsExactly("other");
        assertThat(users.friends(other.id())).isEmpty();
        assertThat(users.friends(me.id()).get(0).presence().state()).isEqualTo(PresenceView.State.OFFLINE);

        users.heartbeat(other.id(), Activity.LOBBY, "yut", "ab12");
        var seen = users.friends(me.id()).get(0).presence();
        assertThat(seen.state()).isEqualTo(PresenceView.State.ONLINE);
        assertThat(seen.roomId()).isEqualTo("ab12");
        assertThat(seen.invitable()).isTrue();

        users.update(other.id(), Presence.BUSY, null);
        assertThat(users.presenceOf(other.id()).state()).isEqualTo(PresenceView.State.BUSY);
        assertThat(users.presenceOf(other.id()).invitable()).isFalse();

        users.update(other.id(), Presence.INVISIBLE, null);
        assertThat(users.presenceOf(other.id())).isEqualTo(PresenceView.OFFLINE);

        assertThat(users.profile(other.id(), me.id())).map(PublicProfile::friend).contains(true);
        users.removeFriend(me.id(), other.id());
        assertThat(users.friends(me.id())).isEmpty();
        assertThatThrownBy(() -> users.addFriend(me.id(), me.id())).hasMessageContaining("자기 자신");
    }

    @Test
    void updatesCharacter() {
        var me = register("me");
        assertThat(users.update(me.id(), null, "dragon").characterId()).isEqualTo("dragon");
        assertThat(users.me(me.id()).presence()).isEqualTo(Presence.ONLINE);
    }
}
