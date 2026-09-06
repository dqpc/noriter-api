package games.noriter.api.user.domain;

import games.noriter.api.user.Presence;
import games.noriter.api.user.UserProfile;
import games.noriter.api.user.UserSummary;
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
public class AppUser {

    public static final String LOCAL = "local";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    /** local 계정은 닉네임 소문자. 닉네임 중복을 대소문자 무시로 막는 키. */
    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(length = 100)
    private String passwordHash;

    @Column(length = 255)
    private String email;

    @Column(length = 32)
    private String characterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Presence presence = Presence.ONLINE;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {}

    public AppUser(String provider, String providerId, String nickname) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.createdAt = Instant.now();
    }

    public static AppUser local(String nickname, String passwordHash, String email, String characterId) {
        var u = new AppUser(LOCAL, key(nickname), nickname);
        u.passwordHash = passwordHash;
        u.email = email;
        u.characterId = characterId;
        return u;
    }

    public static String key(String nickname) {
        return nickname.toLowerCase(java.util.Locale.ROOT);
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getNickname() { return nickname; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmail() { return email; }
    public String getCharacterId() { return characterId; }
    public Presence getPresence() { return presence; }
    public Instant getCreatedAt() { return createdAt; }

    public void setCharacterId(String characterId) { this.characterId = characterId; }
    public void setPresence(Presence presence) { this.presence = presence; }

    public UserSummary toSummary() {
        return new UserSummary(id, nickname);
    }

    public UserProfile toProfile() {
        return new UserProfile(id, nickname, email, characterId, presence, createdAt);
    }
}
