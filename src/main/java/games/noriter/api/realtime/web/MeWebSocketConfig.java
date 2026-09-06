package games.noriter.api.realtime.web;

import games.noriter.api.config.NoriterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@RequiredArgsConstructor
class MeWebSocketConfig implements WebSocketConfigurer {

    private final MeWebSocketHandler handler;
    private final NoriterProperties props;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/me")
                .setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
    }
}
