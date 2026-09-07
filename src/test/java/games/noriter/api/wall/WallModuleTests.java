package games.noriter.api.wall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.support.MutableClock;
import games.noriter.api.support.Tables;
import games.noriter.api.user.PresenceView;
import games.noriter.api.user.PublicProfile;
import games.noriter.api.user.UserService;
import games.noriter.api.wall.infra.WallPostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 시계는 2026-09-08 KST 정오에서 시작한다. 계정 1 goose, 게스트 토큰은 32자 */
@ApplicationModuleTest(extraIncludes = "config")
@ActiveProfiles("test")
class WallModuleTests {

    static final String TOKEN_A = "guest-token-aaaaaaaaaaaaaaaaaaaaaa";
    static final String TOKEN_B = "guest-token-bbbbbbbbbbbbbbbbbbbbbb";
    static final String DEVICE = "1.2.3.4|Mozilla";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(Instant.parse("2026-09-08T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired WallService wall;
    @Autowired WallPostRepository posts;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean UserService users;

    @BeforeEach
    void setUp() {
        Tables.clearUsers(jdbc);
        Tables.insertUser(jdbc, 1, "goose");
        ((MutableClock) clock).set(Instant.parse("2026-09-08T03:00:00Z"));
        var goose = new PublicProfile(1L, "goose", "rat", Instant.EPOCH, PresenceView.OFFLINE, false);
        Mockito.when(users.profile(1L, null)).thenReturn(Optional.of(goose));
        Mockito.when(users.findByNickname(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(users.findByNickname("goose")).thenReturn(Optional.of(goose));
        Mockito.when(users.findByNickname("Goose")).thenReturn(Optional.of(goose));
    }

    private void tick() {
        ((MutableClock) clock).advance(Duration.ofMinutes(1));
    }

    @Test
    void accountWritesOnceADayAndEditsTheSameRow() {
        var first = wall.write(1L, null, null, null, "  오늘도 한 판  ", DEVICE);
        tick();
        var second = wall.write(1L, null, null, null, "고쳐 씀", DEVICE);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.content()).isEqualTo("고쳐 씀");
        assertThat(second.updatedAt()).isAfter(second.createdAt());
        assertThat(second.nickname()).isEqualTo("goose");
        assertThat(second.characterId()).isEqualTo("rat");
        assertThat(second.guest()).isFalse();
        assertThat(posts.count()).isEqualTo(1);
    }

    @Test
    void guestWritesWithNameAndCharacter() {
        var view = wall.write(null, TOKEN_A, " 오리 ", "duck", "꽥", DEVICE);

        assertThat(view.guest()).isTrue();
        assertThat(view.nickname()).isEqualTo("오리");
        assertThat(view.characterId()).isEqualTo("duck");
        assertThat(view.mine()).isTrue();
    }

    @Test
    void guestCannotUseAnAccountNickname() {
        assertThatThrownBy(() -> wall.write(null, TOKEN_A, "Goose", null, "사칭", DEVICE))
                .isInstanceOf(WallException.class)
                .matches(e -> ((WallException) e).kind() == WallException.Kind.NICKNAME_TAKEN);
    }

    @Test
    void sameDeviceWithAnotherGuestTokenIsRejected() {
        wall.write(null, TOKEN_A, "오리", null, "첫 글", DEVICE);

        assertThatThrownBy(() -> wall.write(null, TOKEN_B, "고양이", null, "우회", DEVICE))
                .matches(e -> ((WallException) e).kind() == WallException.Kind.ALREADY_POSTED);
        // 같은 토큰으로 고치는 건 된다
        assertThat(wall.write(null, TOKEN_A, "오리", null, "고침", DEVICE).content()).isEqualTo("고침");
        // 다른 기기는 된다
        assertThat(wall.write(null, TOKEN_B, "고양이", null, "다른 폰", "5.6.7.8|Safari").nickname()).isEqualTo("고양이");
    }

    @Test
    void deleteThenRewriteRevivesTheSameRowAsANewPost() {
        var first = wall.write(null, TOKEN_A, "오리", null, "지울 글", DEVICE);
        tick();
        wall.deleteToday(null, TOKEN_A);
        assertThat(wall.today(null, TOKEN_A)).isEmpty();
        assertThatThrownBy(() -> wall.deleteToday(null, TOKEN_A))
                .matches(e -> ((WallException) e).kind() == WallException.Kind.NOT_FOUND);

        tick();
        var again = wall.write(null, TOKEN_A, "오리2", "cat", "다시 씀", DEVICE);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.nickname()).isEqualTo("오리2");
        assertThat(again.createdAt()).isAfter(first.createdAt());
        assertThat(wall.today(null, TOKEN_A)).singleElement().matches(v -> v.content().equals("다시 씀") && v.mine());
    }

    @Test
    void rejectsBlankTooLongAndTooManyLines() {
        assertThatThrownBy(() -> wall.write(1L, null, null, null, "   ", DEVICE)).matches(invalid());
        assertThatThrownBy(() -> wall.write(1L, null, null, null, "가".repeat(201), DEVICE)).matches(invalid());
        assertThatThrownBy(() -> wall.write(1L, null, null, null, "a\nb\nc\nd\ne\nf\ng", DEVICE)).matches(invalid());
        assertThat(wall.write(1L, null, null, null, "가".repeat(200), DEVICE).content()).hasSize(200);
        assertThat(wall.write(1L, null, null, null, "a\nb\nc\nd\ne\nf", DEVICE).content()).contains("\n");
        assertThatThrownBy(() -> wall.write(null, "short", "오리", null, "글", DEVICE)).matches(invalid());
        assertThatThrownBy(() -> wall.write(null, TOKEN_A, "   ", null, "글", DEVICE)).matches(invalid());
    }

    private static java.util.function.Predicate<Throwable> invalid() {
        return e -> e instanceof WallException w && w.kind() == WallException.Kind.INVALID;
    }

    @Test
    void listsTodayNewestFirstWithMineAndHidesYesterday() {
        wall.write(null, TOKEN_A, "오리", null, "어제 글", DEVICE);
        ((MutableClock) clock).advance(Duration.ofDays(1));

        wall.write(1L, null, null, null, "계정 글", DEVICE);
        tick();
        wall.write(null, TOKEN_B, "고양이", "cat", "게스트 글", "9.9.9.9|Chrome");

        var forGoose = wall.today(1L, null);
        assertThat(forGoose).extracting(WallPostView::content).containsExactly("게스트 글", "계정 글");
        assertThat(forGoose).extracting(WallPostView::mine).containsExactly(false, true);

        var forCat = wall.today(null, TOKEN_B);
        assertThat(forCat).extracting(WallPostView::mine).containsExactly(true, false);
        assertThat(wall.today(null, null)).extracting(WallPostView::mine).containsOnly(false);
    }

    @Test
    void guestWriteCanBeTurnedOff() {
        var closed = new WallService(posts, users, new WallProperties(false), clock);
        assertThatThrownBy(() -> closed.write(null, TOKEN_A, "오리", null, "글", DEVICE))
                .matches(e -> ((WallException) e).kind() == WallException.Kind.FORBIDDEN);
        assertThat(closed.write(1L, null, null, null, "계정은 됨", DEVICE).content()).isEqualTo("계정은 됨");
    }
}
