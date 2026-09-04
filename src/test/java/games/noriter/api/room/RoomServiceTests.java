package games.noriter.api.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.game.GameCatalog;
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
    List<Runnable> scheduled;
    RoomService service;

    @BeforeEach
    void setUp() {
        broadcasts = new ArrayList<>();
        scheduled = new ArrayList<>();
        TaskScheduler scheduler = new TaskScheduler() {
            @Override public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> schedule(Runnable task, Instant startTime) { scheduled.add(task); return null; }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, java.time.Duration period) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, java.time.Duration delay) { throw new UnsupportedOperationException(); }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) { throw new UnsupportedOperationException(); }
        };
        service = new RoomService(new RoomRepository(), new GameCatalog(), List.of(broadcasts::add),
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
    void rejectsUnknownGame() {
        assertThatThrownBy(() -> service.create("nope")).hasMessageContaining("unknown game");
    }
}
