package games.noriter.api.visit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;

@Entity
public class SiteVisit {

    @Id
    private LocalDate visitDay;

    @Column(nullable = false)
    private long count;

    protected SiteVisit() {}

    public SiteVisit(LocalDate visitDay) {
        this.visitDay = visitDay;
    }

    public LocalDate getVisitDay() { return visitDay; }
    public long getCount() { return count; }
}
