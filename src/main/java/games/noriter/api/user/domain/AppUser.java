package games.noriter.api.user.domain;

import games.noriter.api.user.UserSummary;
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
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {}

    public AppUser(String provider, String providerId, String nickname) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getNickname() { return nickname; }
    public Instant getCreatedAt() { return createdAt; }

    public UserSummary toSummary() {
        return new UserSummary(id, nickname);
    }
}
