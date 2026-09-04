package games.noriter.api.room;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class RoomWebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler handler;
    private final List<String> allowedOrigins;

    RoomWebSocketConfig(RoomWebSocketHandler handler,
                        @Value("${noriter.cors.allowed-origins}") List<String> allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/rooms/{roomId}")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
