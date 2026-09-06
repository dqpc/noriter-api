package games.noriter.api.dm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private Long userId;

    /** 읽음 커서. 이보다 큰 id 의 메시지가 안 읽은 것 */
    @Column(nullable = false)
    private long lastReadMessageId;

    @Column(nullable = false)
    private Instant joinedAt;

    protected ConversationMember() {}

    public ConversationMember(Long conversationId, Long userId, Instant joinedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getUserId() { return userId; }
    public long getLastReadMessageId() { return lastReadMessageId; }

    public void readUpTo(long messageId) {
        if (messageId > lastReadMessageId) lastReadMessageId = messageId;
    }
}
