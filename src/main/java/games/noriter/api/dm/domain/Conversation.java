package games.noriter.api.dm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Conversation {

    public enum Kind { DM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    /** 1:1 은 "작은id:큰id". 같은 두 사람의 대화가 하나만 생기게 하는 유니크 키 */
    @Column(length = 64)
    private String dmKey;

    private Instant lastMessageAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Conversation() {}

    public static Conversation dm(Long a, Long b, Instant now) {
        var c = new Conversation();
        c.kind = Kind.DM;
        c.dmKey = dmKey(a, b);
        c.createdAt = now;
        return c;
    }

    public static String dmKey(Long a, Long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public String getDmKey() { return dmKey; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void touched(Instant at) { this.lastMessageAt = at; }
}
