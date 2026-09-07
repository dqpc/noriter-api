package games.noriter.api.wall;

import games.noriter.api.user.PublicProfile;
import games.noriter.api.user.UserService;
import games.noriter.api.wall.domain.WallPost;
import games.noriter.api.wall.infra.WallPostRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WallService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int PAGE = 200;
    static final int GUEST_TOKEN_MIN = 16;
    static final int GUEST_TOKEN_MAX = 64;
    static final int GUEST_NAME_MAX = 12;

    private final WallPostRepository posts;
    private final UserService users;
    private final WallProperties props;
    private final Clock clock;

    /** 오늘 낙서 목록. 계정·게스트 어느 쪽으로든 내 글이면 mine */
    @Transactional(readOnly = true)
    public List<WallPostView> today(Long userId, String guestToken) {
        var rows = posts.findByPostDayAndDeletedAtIsNullOrderByCreatedAtDesc(today(), PageRequest.of(0, PAGE));
        var profiles = new HashMap<Long, PublicProfile>();
        for (var p : rows) {
            if (p.getUserId() != null && !profiles.containsKey(p.getUserId())) {
                users.profile(p.getUserId(), null).ifPresent(pr -> profiles.put(p.getUserId(), pr));
            }
        }
        return rows.stream().map(p -> view(p, profiles, userId, guestToken)).toList();
    }

    /** 오늘 내 글 쓰기 또는 고치기. 하루 한 글이라 두 번째부터는 같은 행을 고친다 */
    @Transactional
    public WallPostView write(Long userId, String guestToken, String guestName, String characterId, String content, String visitorKey) {
        var text = validContent(content);
        var day = today();
        var now = Instant.now(clock);
        var hash = hash(day + "|" + visitorKey);
        WallPost post;
        if (userId != null) {
            post = posts.findByUserIdAndPostDay(userId, day).orElse(null);
            if (post == null) post = posts.save(WallPost.byUser(userId, hash, day, text, now));
            else if (post.isDeleted()) post.revive(null, null, hash, text, now);
            else post.edit(text, now);
        } else {
            if (!props.guestWrite()) throw new WallException(WallException.Kind.FORBIDDEN, "지금은 가입한 사람만 낙서할 수 있어요");
            var token = validGuestToken(guestToken);
            var name = validGuestName(guestName);
            var character = characterId == null || characterId.isBlank() ? null : characterId.strip();
            post = posts.findByGuestTokenAndPostDay(token, day).orElse(null);
            if (post == null || post.isDeleted()) {
                if (posts.existsOtherGuestToday(day, hash, token)) {
                    throw new WallException(WallException.Kind.ALREADY_POSTED, "이 기기에서는 오늘 이미 낙서했어요");
                }
            }
            if (post == null) post = posts.save(WallPost.byGuest(token, name, character, hash, day, text, now));
            else if (post.isDeleted()) post.revive(name, character, hash, text, now);
            else post.edit(text, now);
        }
        var profiles = new HashMap<Long, PublicProfile>();
        if (userId != null) users.profile(userId, null).ifPresent(pr -> profiles.put(userId, pr));
        return view(post, profiles, userId, guestToken);
    }

    @Transactional
    public void deleteToday(Long userId, String guestToken) {
        var day = today();
        var post = userId != null
                ? posts.findByUserIdAndPostDay(userId, day)
                : posts.findByGuestTokenAndPostDay(guestToken == null ? "" : guestToken, day);
        var found = post.filter(p -> !p.isDeleted())
                .orElseThrow(() -> new WallException(WallException.Kind.NOT_FOUND, "오늘 쓴 낙서가 없어요"));
        found.delete(Instant.now(clock));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private WallPostView view(WallPost p, Map<Long, PublicProfile> profiles, Long viewerId, String viewerToken) {
        boolean mine = p.getUserId() != null ? p.getUserId().equals(viewerId)
                : viewerToken != null && viewerToken.equals(p.getGuestToken());
        if (p.isGuest()) {
            return new WallPostView(p.getId(), p.getGuestName(), true, p.getGuestCharacter(), p.getContent(), p.getCreatedAt(), p.getUpdatedAt(), mine);
        }
        var profile = profiles.get(p.getUserId());
        return new WallPostView(p.getId(), profile == null ? "(탈퇴)" : profile.nickname(), false,
                profile == null ? null : profile.characterId(), p.getContent(), p.getCreatedAt(), p.getUpdatedAt(), mine);
    }

    private static String validContent(String content) {
        var text = content == null ? "" : content.strip();
        if (text.isEmpty()) throw new WallException(WallException.Kind.INVALID, "내용을 적어 주세요");
        if (text.codePointCount(0, text.length()) > WallPost.MAX_LENGTH) {
            throw new WallException(WallException.Kind.INVALID, "낙서는 " + WallPost.MAX_LENGTH + "자까지예요");
        }
        if (text.chars().filter(c -> c == '\n').count() > WallPost.MAX_LINE_BREAKS) {
            throw new WallException(WallException.Kind.INVALID, "줄바꿈은 " + WallPost.MAX_LINE_BREAKS + "번까지예요");
        }
        return text;
    }

    private static String validGuestToken(String token) {
        if (token == null || token.length() < GUEST_TOKEN_MIN || token.length() > GUEST_TOKEN_MAX) {
            throw new WallException(WallException.Kind.INVALID, "게스트 토큰이 필요합니다");
        }
        return token;
    }

    private String validGuestName(String name) {
        var n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.codePointCount(0, n.length()) > GUEST_NAME_MAX) {
            throw new WallException(WallException.Kind.INVALID, "이름은 1~" + GUEST_NAME_MAX + "자예요");
        }
        if (users.findByNickname(n).isPresent()) {
            throw new WallException(WallException.Kind.NICKNAME_TAKEN, "가입된 닉네임이에요. 다른 이름으로 낙서해 주세요");
        }
        return n;
    }

    private static String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
