package games.noriter.api.user;

import games.noriter.api.user.domain.AppUser;
import games.noriter.api.user.domain.Friend;
import games.noriter.api.user.infra.AppUserRepository;
import games.noriter.api.user.infra.FriendRepository;
import games.noriter.api.user.infra.JwtTokens;
import games.noriter.api.user.infra.PresenceTracker;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    static final Pattern NICKNAME = Pattern.compile("[\\p{L}\\p{N}_]{2,12}");
    static final int PASSWORD_MIN = 4;

    private final AppUserRepository users;
    private final FriendRepository friends;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final PresenceTracker presence;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public UserSummary findOrCreate(String provider, String providerId, String nickname) {
        return users.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> users.save(new AppUser(provider, providerId, nickname)))
                .toSummary();
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(Long id) {
        return users.findById(id).map(AppUser::toSummary);
    }

    @Transactional(readOnly = true)
    public String characterOf(Long id) {
        return users.findById(id).map(AppUser::getCharacterId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, UserSummary> findSummaries(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.findAllByIdIn(ids).stream()
                .map(AppUser::toSummary)
                .collect(Collectors.toMap(UserSummary::id, s -> s));
    }

    /** 닉네임으로 계정 존재 여부. 대소문자 무시. */
    @Transactional(readOnly = true)
    public Optional<PublicProfile> findByNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return Optional.empty();
        return users.findByProviderAndProviderId(AppUser.LOCAL, AppUser.key(nickname.strip())).map(u -> publicProfile(u, null));
    }

    @Transactional
    public AuthResult register(String nickname, String password, String email, String characterId) {
        var name = nickname == null ? "" : nickname.strip();
        if (!NICKNAME.matcher(name).matches()) throw new UserException(UserException.Kind.INVALID, "닉네임은 2~12자, 글자·숫자·_ 만 됩니다");
        if (password == null || password.length() < PASSWORD_MIN) throw new UserException(UserException.Kind.INVALID, "비밀번호는 " + PASSWORD_MIN + "자 이상이어야 합니다");
        if (users.findByProviderAndProviderId(AppUser.LOCAL, AppUser.key(name)).isPresent()) throw new UserException(UserException.Kind.DUPLICATE, "이미 있는 닉네임입니다");
        var mail = email == null || email.isBlank() ? null : email.strip();
        var user = users.save(AppUser.local(name, passwords.encode(password), mail, characterId));
        events.publishEvent(new UserRegistered(user.getId(), user.getNickname()));
        return new AuthResult(tokens.issue(user.getId(), user.getNickname()), user.toProfile());
    }

    @Transactional
    public AuthResult login(String nickname, String password) {
        var user = users.findByProviderAndProviderId(AppUser.LOCAL, AppUser.key(nickname == null ? "" : nickname.strip()))
                .filter(u -> u.getPasswordHash() != null && passwords.matches(password == null ? "" : password, u.getPasswordHash()))
                .orElseThrow(() -> new UserException(UserException.Kind.UNAUTHORIZED, "비밀번호가 맞지 않습니다"));
        user.seen(Instant.now(clock));
        return new AuthResult(tokens.issue(user.getId(), user.getNickname()), user.toProfile());
    }

    /** 개인 채널이 붙을 때. 마지막 접속 시각만 남긴다 */
    @Transactional
    public void markSeen(Long userId) {
        users.findById(userId).ifPresent(u -> u.seen(Instant.now(clock)));
    }

    /** Bearer 토큰 → 계정. 만료·위조면 empty. */
    @Transactional(readOnly = true)
    public Optional<UserProfile> authenticate(String token) {
        return tokens.subject(token).flatMap(users::findById).map(AppUser::toProfile);
    }

    @Transactional(readOnly = true)
    public UserProfile me(Long userId) {
        return require(userId).toProfile();
    }

    @Transactional
    public UserProfile update(Long userId, Presence presence, String characterId) {
        var user = require(userId);
        if (presence != null) user.setPresence(presence);
        if (characterId != null) user.setCharacterId(characterId.isBlank() ? null : characterId);
        return user.toProfile();
    }

    public void heartbeat(Long userId, Activity activity, String gameId, String roomId) {
        presence.touch(userId, activity == null ? Activity.MENU : activity, gameId, roomId);
    }

    /** 활동 내용은 그대로 두고 살아 있음만 갱신 */
    public void touch(Long userId) {
        presence.touch(userId);
    }

    public void offline(Long userId) {
        presence.clear(userId);
    }

    @Transactional(readOnly = true)
    public PresenceView presenceOf(Long userId) {
        return users.findById(userId).map(this::presenceOf).orElse(PresenceView.OFFLINE);
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfile> profile(Long targetId, Long viewerId) {
        return users.findById(targetId).map(u -> publicProfile(u, viewerId));
    }

    @Transactional(readOnly = true)
    public List<FriendView> friends(Long userId) {
        var links = friends.findAllByUserIdOrderByCreatedAtAsc(userId);
        var byId = users.findAllByIdIn(links.stream().map(Friend::getFriendId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        return links.stream()
                .map(l -> byId.get(l.getFriendId()))
                .filter(u -> u != null)
                .map(u -> new FriendView(u.getId(), u.getNickname(), u.getCharacterId(), presenceOf(u)))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isFriend(Long userId, Long friendId) {
        return friends.findByUserIdAndFriendId(userId, friendId).isPresent();
    }

    @Transactional
    public void addFriend(Long userId, Long friendId) {
        if (userId.equals(friendId)) throw new UserException(UserException.Kind.INVALID, "자기 자신은 추가할 수 없습니다");
        if (users.findById(friendId).isEmpty()) throw new UserException(UserException.Kind.NOT_FOUND, "없는 사용자입니다");
        if (friends.findByUserIdAndFriendId(userId, friendId).isEmpty()) friends.save(new Friend(userId, friendId));
    }

    @Transactional
    public void removeFriend(Long userId, Long friendId) {
        friends.findByUserIdAndFriendId(userId, friendId).ifPresent(friends::delete);
    }

    private PublicProfile publicProfile(AppUser u, Long viewerId) {
        boolean friend = viewerId != null && !viewerId.equals(u.getId()) && isFriend(viewerId, u.getId());
        return new PublicProfile(u.getId(), u.getNickname(), u.getCharacterId(), u.getCreatedAt(), presenceOf(u), friend);
    }

    private PresenceView presenceOf(AppUser u) {
        if (u.getPresence() == Presence.INVISIBLE) return PresenceView.OFFLINE;
        return presence.current(u.getId())
                .map(b -> new PresenceView(switch (u.getPresence()) {
                    case AWAY -> PresenceView.State.AWAY;
                    case BUSY -> PresenceView.State.BUSY;
                    default -> PresenceView.State.ONLINE;
                }, b.activity(), b.gameId(), b.roomId()))
                .orElse(PresenceView.OFFLINE);
    }

    private AppUser require(Long userId) {
        return users.findById(userId).orElseThrow(() -> new UserException(UserException.Kind.NOT_FOUND, "없는 사용자입니다"));
    }
}
