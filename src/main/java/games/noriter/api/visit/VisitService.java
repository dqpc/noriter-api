package games.noriter.api.visit;

import games.noriter.api.visit.domain.SiteVisit;
import games.noriter.api.visit.infra.SiteVisitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VisitService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SiteVisitRepository visits;
    private final Clock clock;

    @Transactional
    public VisitStats record() {
        var day = LocalDate.now(clock.withZone(ZONE));
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
}
