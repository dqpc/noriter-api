package games.noriter.api.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.game.GameCatalog;
import games.noriter.api.room.infra.InMemoryRoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class RoomServiceTests {

    static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    List<RoomSnapshot> broadcasts;
    List<RoomChatMessage> chats;
    List<RoomGameState> states;
    List<Runnable> scheduled;
    RoomService service;

    @BeforeEach
    void setUp() {
        broadcasts = new ArrayList<>();
        chats = new ArrayList<>();
        states = new ArrayList<>();
        scheduled = new ArrayList<>();
        TaskScheduler scheduler = new TaskScheduler() {
            @Override public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> schedule(Runnable task, Instant startTime) { scheduled.add(task); return null; }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, java.time.Duration period) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, java.time.Duration delay) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) { throw new UnsupportedOperationException(); }
        };
        games.noriter.api.room.domain.RoomBroadcaster b = new games.noriter.api.room.domain.RoomBroadcaster() {
            public void broadcast(RoomSnapshot s) { broadcasts.add(s); }
            public void chat(RoomChatMessage m) { chats.add(m); }
            public void gameState(RoomGameState s) { states.add(s); }
        };
        service = new RoomService(new InMemoryRoomRepository(), new GameCatalog(new games.noriter.api.config.NoriterProperties(new games.noriter.api.config.NoriterProperties.Cors(List.of()), new games.noriter.api.config.NoriterProperties.Game(false)), List.of(new games.noriter.api.game.stairs.StairsShared())), List.of(b),
                scheduler, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsRoomWithDefaultsFromGameSpec() {
        var room = service.create("2048");
        assertThat(room.id()).hasSize(8);
        assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.maxPlayers()).isEqualTo(4);
        assertThat(room.players()).isEmpty();
    }

    @Test
    void firstJoinerBecomesHostAndOnlyHostChangesSettings() {
        var id = service.create("2048").id();
        service.join(id, "a", "A");
        var room = service.join(id, "b", "B");
        assertThat(room.hostId()).isEqualTo("a");

        assertThatThrownBy(() -> service.setMaxPlayers(id, "b", 2)).isInstanceOf(RoomException.class);
        assertThat(service.setMaxPlayers(id, "a", 2).maxPlayers()).isEqualTo(2);
        assertThatThrownBy(() -> service.join(id, "c", "C")).hasMessageContaining("full");
        assertThatThrownBy(() -> service.setMaxPlayers(id, "a", 9)).hasMessageContaining("between");
    }

    @Test
    void optionsDefaultFromSpecAndHostCanChangeWithinChoices() {
        var id = service.create("2048").id();
        assertThat(service.find(id).orElseThrow().options()).containsEntry("target", 2048);
        service.join(id, "a", "A");
        service.join(id, "b", "B");

        assertThat(service.setOptions(id, "a", java.util.Map.of("target", 512)).options()).containsEntry("target", 512);
        assertThat(service.setOptions(id, "a", java.util.Map.of("target", "1024")).options()).containsEntry("target", 1024);
        assertThatThrownBy(() -> service.setOptions(id, "b", java.util.Map.of("target", 512))).hasMessageContaining("host");
        assertThatThrownBy(() -> service.setOptions(id, "a", java.util.Map.of("target", 4096))).hasMessageContaining("invalid option");
        assertThatThrownBy(() -> service.setOptions(id, "a", java.util.Map.of("speed", 1))).hasMessageContaining("invalid option");
    }

    @Test
    void hostLeavingPromotesNextPlayer() {
        var id = service.create("2048").id();
        service.join(id, "a", "A");
        service.join(id, "b", "B");
        service.leave(id, "a");
        assertThat(service.find(id).orElseThrow().hostId()).isEqualTo("b");
        service.leave(id, "b");
        assertThat(service.find(id)).isEmpty();
    }

    @Test
    void fullMatchLifecycleRanksByScore() {
        var id = service.create("2048").id();
        service.join(id, "a", "A");
        service.join(id, "b", "B");
        service.join(id, "c", "C");

        var countdown = service.start(id, "a");
        assertThat(countdown.status()).isEqualTo(RoomStatus.COUNTDOWN);
        assertThat(countdown.startAt()).isEqualTo(NOW.plus(RoomService.COUNTDOWN));
        assertThat(countdown.endAt()).isEqualTo(countdown.startAt().plusSeconds(180));
        assertThat(countdown.seed()).isNotZero();
        assertThat(scheduled).hasSize(2);
        assertThatThrownBy(() -> service.score(id, "a", 10)).hasMessageContaining("not running");

        scheduled.get(0).run();
        assertThat(service.find(id).orElseThrow().status()).isEqualTo(RoomStatus.PLAYING);

        service.score(id, "a", 100);
        service.score(id, "a", 50);
        service.score(id, "b", 300);
        service.finish(id, "a", 120);
        service.finish(id, "b", 300);
        assertThat(service.find(id).orElseThrow().status()).isEqualTo(RoomStatus.PLAYING);

        scheduled.get(1).run();
        var done = service.find(id).orElseThrow();
        assertThat(done.status()).isEqualTo(RoomStatus.FINISHED);
        assertThat(done.players()).extracting(RoomSnapshot.PlayerSnapshot::id, RoomSnapshot.PlayerSnapshot::rank)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", 2),
                        org.assertj.core.groups.Tuple.tuple("b", 1),
                        org.assertj.core.groups.Tuple.tuple("c", 3));
        assertThat(broadcasts).isNotEmpty();
    }

    @Test
    void tiedScoresShareRank() {
        var id = service.create("2048").id();
        service.join(id, "a", "A");
        service.join(id, "b", "B");
        service.start(id, "a");
        scheduled.get(0).run();
        service.finish(id, "a", 100);
        service.finish(id, "b", 100);
        var done = service.find(id).orElseThrow();
        assertThat(done.status()).isEqualTo(RoomStatus.FINISHED);
        assertThat(done.players()).allMatch(p -> p.rank() == 1);
    }

    @Test
    void chatIsDeliveredAndKeptInHistoryWithSystemMessages() {
        var id = service.create("2048").id();
        service.join(id, "a", "A");
        service.chat(id, "a", "  hello  ");
        service.chat(id, "a", "   ");
        assertThatThrownBy(() -> service.chat(id, "zzz", "hi")).hasMessageContaining("not in room");
        assertThatThrownBy(() -> service.chat(id, "a", "x".repeat(201))).hasMessageContaining("too long");
        service.join(id, "b", "B");
        service.leave(id, "b");

        assertThat(chats).extracting(RoomChatMessage::text)
                .containsExactly("A 님이 들어왔습니다", "hello", "B 님이 들어왔습니다", "B 님이 나갔습니다");
        assertThat(chats.get(1).system()).isFalse();
        assertThat(chats.get(1).nickname()).isEqualTo("A");
        assertThat(service.chatHistory(id)).hasSize(4);
    }

    @Test
    void gameWithoutDurationEndsOnlyWhenAllFinish() {
        var id = service.create("stairs").id();
        service.join(id, "a", "A");
        service.join(id, "b", "B");
        var cd = service.start(id, "a");
        assertThat(cd.endAt()).isNull();
        assertThat(scheduled).hasSize(1);
        scheduled.get(0).run();
        service.finish(id, "a", 30);
        assertThat(service.find(id).orElseThrow().status()).isEqualTo(RoomStatus.PLAYING);
        service.finish(id, "b", 45);
        var done = service.find(id).orElseThrow();
        assertThat(done.status()).isEqualTo(RoomStatus.FINISHED);
        assertThat(done.players()).extracting(RoomSnapshot.PlayerSnapshot::id, RoomSnapshot.PlayerSnapshot::rank)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("a", 2), org.assertj.core.groups.Tuple.tuple("b", 1));
    }

    @Test
    void coopRoomSharesOneCharacterBetweenTwoPlayers() {
        var id = service.create("stairs", games.noriter.api.game.GameMode.COOP).id();
        var room = service.find(id).orElseThrow();
        assertThat(room.mode()).isEqualTo(games.noriter.api.game.GameMode.COOP);
        assertThat(room.maxPlayers()).isEqualTo(2);
        service.join(id, "a", "A");
        assertThatThrownBy(() -> service.setMaxPlayers(id, "a", 4)).hasMessageContaining("fixed");
        assertThatThrownBy(() -> service.start(id, "a")).hasMessageContaining("not enough");
        service.join(id, "b", "B");
        service.start(id, "a");
        scheduled.get(0).run();
        assertThat(service.find(id).orElseThrow().status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(states).hasSize(1);
        @SuppressWarnings("unchecked") var roles = (java.util.Map<String, String>) states.get(0).view().get("roles");
        assertThat(roles).containsEntry("a", "L").containsEntry("b", "R");
        var pattern = (String) states.get(0).view().get("pattern");

        char first = pattern.charAt(1);
        String owner = first == 'L' ? "a" : "b";
        String other = first == 'L' ? "b" : "a";
        service.input(id, other, java.util.Map.of("dir", String.valueOf(first)));
        assertThat((Integer) states.get(states.size() - 1).view().get("steps")).isEqualTo(0);
        service.input(id, owner, java.util.Map.of("dir", String.valueOf(first)));
        assertThat((Integer) states.get(states.size() - 1).view().get("steps")).isEqualTo(1);
        assertThat(scheduled.size()).isGreaterThanOrEqualTo(2);

        char second = pattern.charAt(2);
        String wrongOwner = second == 'L' ? "b" : "a";
        service.input(id, wrongOwner, java.util.Map.of("dir", second == 'L' ? "R" : "L"));
        var done = service.find(id).orElseThrow();
        assertThat(done.status()).isEqualTo(RoomStatus.FINISHED);
        assertThat(done.players()).allMatch(p -> p.score() == 1 && p.rank() == 1);
    }

    @Test
    void coopRejectedForGamesWithoutSharedEngine() {
        assertThatThrownBy(() -> service.create("2048", games.noriter.api.game.GameMode.COOP)).hasMessageContaining("mode not supported");
    }

    @Test
    void rejectsUnknownGame() {
        assertThatThrownBy(() -> service.create("nope")).hasMessageContaining("unknown game");
    }
}
