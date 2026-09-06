package games.noriter.api.visit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.io.Serializable;
import java.time.LocalDate;

/** 하루에 한 번만 세기 위한 방문자 표시. 원본 IP 는 남기지 않고 해시만 둔다 */
@Entity
public class SiteVisitor {

    @EmbeddedId
    private Key key;

    protected SiteVisitor() {}

    public SiteVisitor(LocalDate day, String visitorHash) {
        this.key = new Key(day, visitorHash);
    }

    @Embeddable
    public record Key(
            @Column(name = "visit_day") LocalDate visitDay,
            @Column(name = "visitor_hash") String visitorHash) implements Serializable {}
}
