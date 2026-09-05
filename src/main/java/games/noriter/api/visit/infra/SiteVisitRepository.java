package games.noriter.api.visit.infra;

import games.noriter.api.visit.domain.SiteVisit;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, LocalDate> {

    @Modifying
    @Query("update SiteVisit v set v.count = v.count + 1 where v.visitDay = :day")
    int increment(LocalDate day);

    @Query("select coalesce(sum(v.count), 0) from SiteVisit v")
    long total();

    @Query("select v.count from SiteVisit v where v.visitDay = :day")
    Optional<Long> countOf(LocalDate day);
}
