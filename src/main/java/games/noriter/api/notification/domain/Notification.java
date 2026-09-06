package games.noriter.api.notification.domain;

import games.noriter.api.notification.NotificationKind;
import games.noriter.api.notification.NotificationView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationKind kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 400)
    private String body;

    @Column(length = 200)
    private String link;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    protected Notification() {}

    public Notification(Long userId, NotificationKind kind, String title, String body, String link, Instant createdAt) {
        this.userId = userId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.link = link;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getReadAt() { return readAt; }

    public void markRead(Instant at) {
        if (readAt == null) readAt = at;
    }

    public NotificationView toView() {
        return new NotificationView(id, kind, title, body, link, createdAt, readAt != null);
    }
}
