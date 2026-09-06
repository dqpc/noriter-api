package games.noriter.api.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import games.noriter.api.room.RoomInvited;
import games.noriter.api.support.Tables;
import games.noriter.api.user.PresenceView;
import games.noriter.api.user.UserService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeWebSocketTests {

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcTemplate jdbc;

    class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        final WebSocketSession session;

        Client(String token) throws Exception {
            session = new StandardWebSocketClient()
                    .execute(this, "ws://localhost:" + port + "/ws/me?token=" + token).get(5, TimeUnit.SECONDS);
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage m) {
            inbox.add(json.readTree(m.getPayload()));
        }

        void send(String payload) throws Exception {
            session.sendMessage(new TextMessage(payload));
        }

        JsonNode next() throws Exception {
            var m = inbox.poll(5, TimeUnit.SECONDS);
            assertThat(m).as("message within 5s").isNotNull();
            return m;
        }
    }

    @BeforeEach
    void setUp() {
        Tables.clearUsers(jdbc);
    }

    @Test
    void connectedUserIsOnlineAndReceivesNotificationsLive() throws Exception {
        var goose = users.register("goose", "pass1234", null, "tiger");
        var duck = users.register("duck", "pass1234", null, "rat");
        var client = new Client(goose.token());

        var hello = client.next();
        assertThat(hello.path("type").asText()).isEqualTo("hello");
        assertThat(users.presenceOf(goose.user().id()).state()).isEqualTo(PresenceView.State.ONLINE);

        client.send("{\"type\":\"activity\",\"activity\":\"LOBBY\",\"gameId\":\"yut\",\"roomId\":\"ab12\"}");
        client.send("{\"type\":\"ping\"}");
        assertThat(client.next().path("type").asText()).isEqualTo("pong");
        var seen = users.presenceOf(goose.user().id());
        assertThat(seen.roomId()).isEqualTo("ab12");

        events.publishEvent(new RoomInvited("ab12", "yut", "윷놀이", duck.user().id(), "duck", goose.user().id()));
        var pushed = client.next();
        assertThat(pushed.path("type").asText()).isEqualTo("notification");
        assertThat(pushed.path("item").path("kind").asText()).isEqualTo("INVITE");
        assertThat(pushed.path("unread").asLong()).isEqualTo(2);

        client.session.close();
        Thread.sleep(300);
        assertThat(users.presenceOf(goose.user().id()).state()).isEqualTo(PresenceView.State.OFFLINE);
    }

    @Test
    void rejectsBadToken() throws Exception {
        var client = new Client("not-a-token");
        Thread.sleep(300);
        assertThat(client.session.isOpen()).isFalse();
    }
}
