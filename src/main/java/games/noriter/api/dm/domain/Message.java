package games.noriter.api.dm.domain;

import games.noriter.api.dm.MessageView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Message {

    public static final int MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected Message() {}

    public Message(Long conversationId, Long senderId, String body, Instant createdAt) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getSenderId() { return senderId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }

    public MessageView toView() {
        return new MessageView(id, conversationId, senderId, body, createdAt);
    }
}
