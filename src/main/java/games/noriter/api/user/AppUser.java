package games.noriter.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_user")
class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {}

    AppUser(String provider, String providerId, String nickname) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.createdAt = Instant.now();
    }

    Long getId() { return id; }
    String getProvider() { return provider; }
    String getProviderId() { return providerId; }
    String getNickname() { return nickname; }
    Instant getCreatedAt() { return createdAt; }

    UserSummary toSummary() {
        return new UserSummary(id, nickname);
    }
}
