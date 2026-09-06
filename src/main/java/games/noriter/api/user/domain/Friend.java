package games.noriter.api.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

/** 일방향 친구(팔로우). user 가 friend 를 추가한 것이고 상대는 모른다. */
@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long friendId;

    @Column(nullable = false)
    private Instant createdAt;

    protected Friend() {}

    public Friend(Long userId, Long friendId) {
        this.userId = userId;
        this.friendId = friendId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getFriendId() { return friendId; }
    public Instant getCreatedAt() { return createdAt; }
}
