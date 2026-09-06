package games.noriter.api.realtime.web.dto;

public sealed interface MeServerMessage {

    record Hello(String type) implements MeServerMessage {
        public Hello() { this("hello"); }
    }

    record Pong(String type) implements MeServerMessage {
        public Pong() { this("pong"); }
    }
}
