package games.noriter.api.realtime.infra;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/** 사용자별 `/ws/me` 세션. 탭을 여러 개 열면 세션도 여러 개. */
@Component
@RequiredArgsConstructor
public class MeSessions {

    private static final Logger log = LoggerFactory.getLogger(MeSessions.class);
    private static final String USER_ATTR = "userId";

    private final ObjectMapper json;
    private final Map<Long, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();

    public static void bind(WebSocketSession session, Long userId) {
        session.getAttributes().put(USER_ATTR, userId);
    }

    public static Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(USER_ATTR);
    }

    public void add(Long userId, WebSocketSession session) {
        byUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    /** @return 이 사용자의 마지막 세션이 빠졌으면 true */
    public boolean remove(Long userId, WebSocketSession session) {
        var set = byUser.get(userId);
        if (set == null) return true;
        set.remove(session);
        if (!set.isEmpty()) return false;
        byUser.remove(userId, set);
        return true;
    }

    public boolean isConnected(Long userId) {
        var set = byUser.get(userId);
        return set != null && !set.isEmpty();
    }

    public void push(Long userId, Object payload) {
        var set = byUser.get(userId);
        if (set == null) return;
        set.forEach(s -> send(s, payload));
    }

    public void send(WebSocketSession session, Object payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
            }
        } catch (IOException e) {
            log.warn("send failed to {}: {}", session.getId(), e.getMessage());
        }
    }
}
