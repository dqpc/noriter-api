package games.noriter.api.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트에서 시각을 앞으로 돌릴 수 있는 시계 (자정 넘김·작성 순서 검증용). withZone 한 시계도 같은 시각을 본다 */
public final class MutableClock extends Clock {

    private final Instant[] now;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this(new Instant[] {start}, zone);
    }

    private MutableClock(Instant[] shared, ZoneId zone) {
        this.now = shared;
        this.zone = zone;
    }

    public void advance(Duration d) { now[0] = now[0].plus(d); }
    public void set(Instant at) { now[0] = at; }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { return new MutableClock(now, z); }
    @Override public Instant instant() { return now[0]; }
}
