package games.noriter.api.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomWebSocketTests {

    @LocalServerPort int port;
    @Autowired ObjectMapper json;

    class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        WebSocketSession session;
        String playerId;

        Client(String roomId) throws Exception {
            session = new StandardWebSocketClient()
                    .execute(this, "ws://localhost:" + port + "/ws/rooms/" + roomId).get(5, TimeUnit.SECONDS);
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage m) {
            inbox.add(json.readTree(m.getPayload()));
        }

        void send(Map<String, Object> msg) throws Exception {
            session.sendMessage(new TextMessage(json.writeValueAsString(msg)));
        }

        JsonNode next() throws Exception {
            var m = inbox.poll(5, TimeUnit.SECONDS);
            assertThat(m).as("message within 5s").isNotNull();
            return m;
        }

        JsonNode nextRoom() throws Exception {
            JsonNode m;
            do { m = next(); } while (!"room".equals(m.path("type").asText()));
            return m.get("room");
        }
    }

    @Test
    void twoPlayersJoinAndHostStartsMatch() throws Exception {
        var rest = RestClient.create("http://localhost:" + port);
        var created = rest.post().uri("/api/rooms").body(Map.of("gameId", "2048")).retrieve().body(JsonNode.class);
        var roomId = created.get("id").asText();
        assertThat(roomId).hasSize(8);

        var host = new Client(roomId);
        var hello = host.next();
        assertThat(hello.get("type").asText()).isEqualTo("hello");
        host.playerId = hello.get("playerId").asText();
        host.send(Map.of("type", "join", "nickname", "goose"));
        var r1 = host.nextRoom();
        assertThat(r1.get("players")).hasSize(1);
        assertThat(r1.get("hostId").asText()).isEqualTo(host.playerId);

        var guest = new Client(roomId);
        guest.next();
        guest.send(Map.of("type", "join", "nickname", "duck"));
        assertThat(host.nextRoom().get("players")).hasSize(2);
        assertThat(guest.nextRoom().get("players")).hasSize(2);

        guest.send(Map.of("type", "settings", "maxPlayers", 2));
        assertThat(guest.next().get("type").asText()).isEqualTo("error");

        host.send(Map.of("type", "settings", "options", Map.of("target", 512)));
        assertThat(guest.nextRoom().get("options").get("target").asInt()).isEqualTo(512);
        host.nextRoom();

        host.send(Map.of("type", "start"));
        var countdown = guest.nextRoom();
        assertThat(countdown.get("status").asText()).isEqualTo("COUNTDOWN");
        assertThat(countdown.get("seed").asLong()).isNotZero();
        host.nextRoom();

        assertThat(host.nextRoom().get("status").asText()).isEqualTo("PLAYING");
        guest.nextRoom();

        host.send(Map.of("type", "score", "score", 512));
        assertThat(guest.nextRoom().get("players").get(0).get("score").asLong()).isEqualTo(512);

        var rest404 = rest.get().uri("/api/rooms/zzzzzzzz").exchange((req, res) -> res.getStatusCode().value());
        assertThat(rest404).isEqualTo(404);

        host.session.close();
        guest.session.close();
    }
}
