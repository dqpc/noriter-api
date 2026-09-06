package games.noriter.api.user.infra;

import games.noriter.api.user.Activity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 브라우저 하트비트로 갱신되는 접속 상태. 메모리에만 있어 재시작하면 전원 오프라인으로 시작한다. */
@Component
@RequiredArgsConstructor
public class PresenceTracker {

    public static final Duration TTL = Duration.ofSeconds(75);

    public record Heartbeat(Instant at, Activity activity, String gameId, String roomId) {}

    private final Clock clock;
    private final Map<Long, Heartbeat> beats = new ConcurrentHashMap<>();

    public void touch(Long userId, Activity activity, String gameId, String roomId) {
        beats.put(userId, new Heartbeat(Instant.now(clock), activity, gameId, roomId));
    }

    public void touch(Long userId) {
        beats.compute(userId, (k, b) -> b == null
                ? new Heartbeat(Instant.now(clock), Activity.MENU, null, null)
                : new Heartbeat(Instant.now(clock), b.activity(), b.gameId(), b.roomId()));
    }

    public void clear(Long userId) {
        beats.remove(userId);
    }

    public Optional<Heartbeat> current(Long userId) {
        var b = beats.get(userId);
        if (b == null) return Optional.empty();
        if (b.at().plus(TTL).isBefore(Instant.now(clock))) {
            beats.remove(userId, b);
            return Optional.empty();
        }
        return Optional.of(b);
    }
}
