package games.noriter.api.visit;

import games.noriter.api.visit.domain.SiteVisit;
import games.noriter.api.visit.domain.SiteVisitor;
import games.noriter.api.visit.infra.SiteVisitRepository;
import games.noriter.api.visit.infra.SiteVisitorRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VisitService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SiteVisitRepository visits;
    private final SiteVisitorRepository visitors;
    private final Clock clock;

    /** 같은 방문자(IP+브라우저)는 하루 한 번만 센다. 브라우저가 반복 호출해도 늘지 않는다 */
    @Transactional
    public VisitStats record(String visitorKey) {
        var day = LocalDate.now(clock.withZone(ZONE));
        var id = new SiteVisitor.Key(day, hash(day + "|" + visitorKey));
        if (visitors.existsById(id)) return stats();
        visitors.save(new SiteVisitor(day, id.visitorHash()));
        if (visits.increment(day) == 0) {
            try {
                visits.saveAndFlush(new SiteVisit(day));
            } catch (DataIntegrityViolationException ignored) {
                // 동시에 첫 방문이 들어온 경우
            }
            visits.increment(day);
        }
        return stats();
    }

    @Transactional(readOnly = true)
    public VisitStats stats() {
        var day = LocalDate.now(clock.withZone(ZONE));
        return new VisitStats(visits.countOf(day).orElse(0L), visits.total());
    }

    private static String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
