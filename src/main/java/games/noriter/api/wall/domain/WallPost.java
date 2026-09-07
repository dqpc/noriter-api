package games.noriter.api.wall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 하루 한 줄 낙서. 삭제는 deleted_at 으로 표시하되 @SoftDelete 는 쓰지 않는다 —
 * 같은 날 다시 쓰면 (작성자, 날짜) 유니크 때문에 새 행이 아니라 지운 행을 되살려야 하기 때문.
 */
@Entity
public class WallPost {

    public static final int MAX_LENGTH = 200;
    public static final int MAX_LINE_BREAKS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 64)
    private String guestToken;

    @Column(length = 12)
    private String guestName;

    @Column(length = 16)
    private String guestCharacter;

    @Column(nullable = false, length = 64)
    private String visitorHash;

    @Column(nullable = false)
    private LocalDate postDay;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    protected WallPost() {}

    public static WallPost byUser(Long userId, String visitorHash, LocalDate day, String content, Instant now) {
        var p = new WallPost();
        p.userId = userId;
        p.init(visitorHash, day, content, now);
        return p;
    }

    public static WallPost byGuest(String token, String name, String character, String visitorHash, LocalDate day, String content, Instant now) {
        var p = new WallPost();
        p.guestToken = token;
        p.guestName = name;
        p.guestCharacter = character;
        p.init(visitorHash, day, content, now);
        return p;
    }

    private void init(String visitorHash, LocalDate day, String content, Instant now) {
        this.visitorHash = visitorHash;
        this.postDay = day;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(String content, Instant now) {
        this.content = content;
        this.updatedAt = now;
    }

    /** 지운 글 자리에 다시 쓰는 것은 새 글로 본다 — 목록 순서(작성 시각)도 지금으로 */
    public void revive(String name, String character, String visitorHash, String content, Instant now) {
        if (guestToken != null) {
            this.guestName = name;
            this.guestCharacter = character;
        }
        this.visitorHash = visitorHash;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
        this.deletedAt = null;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isGuest() { return guestToken != null; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getGuestToken() { return guestToken; }
    public String getGuestName() { return guestName; }
    public String getGuestCharacter() { return guestCharacter; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
