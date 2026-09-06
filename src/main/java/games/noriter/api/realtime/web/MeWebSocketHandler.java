package games.noriter.api.realtime.web;

import games.noriter.api.realtime.infra.MeSessions;
import games.noriter.api.realtime.web.dto.MeClientMessage;
import games.noriter.api.realtime.web.dto.MeServerMessage;
import games.noriter.api.user.Activity;
import games.noriter.api.user.UserService;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 로그인한 브라우저가 하나씩 여는 개인 채널 `/ws/me?token=JWT`.
 * 연결이 살아 있으면 온라인이고, 알림·쪽지 같은 푸시가 이 채널로 온다. 연결 직후 hello 만 보내고 내용은 각 모듈의 REST 로 읽는다.
 */
@Component
@RequiredArgsConstructor
class MeWebSocketHandler extends TextWebSocketHandler {

    private final UserService users;
    private final MeSessions sessions;
    private final ObjectMapper json;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        var account = users.authenticate(tokenOf(session.getUri()));
        if (account.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("unauthorized"));
            return;
        }
        var userId = account.get().id();
        MeSessions.bind(session, userId);
        sessions.add(userId, session);
        users.heartbeat(userId, Activity.MENU, null, null);
        users.markSeen(userId);
        sessions.send(session, new MeServerMessage.Hello());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var userId = MeSessions.userId(session);
        if (userId == null) return;
        try {
            switch (json.readValue(message.getPayload(), MeClientMessage.class)) {
                case MeClientMessage.ActivityUpdate m -> users.heartbeat(userId, m.activity(), m.gameId(), m.roomId());
                case MeClientMessage.Ping m -> {
                    users.touch(userId);
                    sessions.send(session, new MeServerMessage.Pong());
                }
            }
        } catch (RuntimeException e) {
            // 잘못된 메시지는 무시. 개인 채널이라 에러 회신이 의미 없다
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var userId = MeSessions.userId(session);
        if (userId == null) return;
        if (sessions.remove(userId, session)) users.offline(userId);
    }

    private static String tokenOf(URI uri) {
        if (uri == null) return null;
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }
}
