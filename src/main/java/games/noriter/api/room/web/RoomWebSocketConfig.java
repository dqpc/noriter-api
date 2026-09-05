package games.noriter.api.room.web;

import games.noriter.api.config.NoriterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
class RoomWebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler handler;
    private final NoriterProperties props;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/rooms/{roomId}")
                .setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
    }
}
