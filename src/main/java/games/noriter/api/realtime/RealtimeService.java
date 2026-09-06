package games.noriter.api.realtime;

import games.noriter.api.realtime.infra.MeSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 로그인한 브라우저의 개인 채널(`/ws/me`)로 메시지를 밀어 넣는다. 알림·쪽지 등 모듈이 공용으로 쓴다. */
@Service
@RequiredArgsConstructor
public class RealtimeService {

    private final MeSessions sessions;

    /** payload 는 `type` 필드를 가진 record. 접속이 없으면 아무 일도 없다 */
    public void send(Long userId, Object payload) {
        sessions.push(userId, payload);
    }

    public boolean isConnected(Long userId) {
        return sessions.isConnected(userId);
    }
}
