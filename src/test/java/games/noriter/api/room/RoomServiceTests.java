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
    List<RoomPlayerState> relayed;
    List<RoomGameState> states;
    List<Runnable> scheduled;
    List<Object> published;
    RoomService service;

    @BeforeEach
    void setUp() {
        broadcasts = new ArrayList<>();
        chats = new ArrayList<>();
        relayed = new ArrayList<>();
        states = new ArrayList<>();
        scheduled = new ArrayList<>();
        published = new ArrayList<>();
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
            public void playerState(RoomPlayerState s) { relayed.add(s); }
            public void gameState(RoomGameState s) { states.add(s); }
        };
        service = new RoomService(new InMemoryRoomRepository(), new GameCatalog(new games.noriter.api.config.NoriterProperties(new games.noriter.api.config.NoriterProperties.Cors(List.of()), new games.noriter.api.config.NoriterProperties.Game(false), new games.noriter.api.config.NoriterProperties.Auth("x")), List.of(new games.noriter.api.game.yut.YutGame())), List.of(b),
                scheduler, Clock.fixed(NOW, ZoneOffset.UTC), published::add);
    }

    @Test
    void createsRoomWithDefaultsFromGameSpec() {
        var room = service.create("2048");
        assertThat(room.id()).hasSize(4);
        assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.maxPlayers()).isEqualTo(4);
        assertThat(room.players()).isEmpty();
    }

    @Test
    void firstJoinerBecomesHostAndOnlyHostChangesSettings() {
        var id = service.create("2048").id();
        service.join(id, "a", "A", "rabbit", null);
        var room = service.join(id, "b", "B", "rabbit", null);
        assertThat(room.hostId()).isEqualTo("a");

        assertThatThrownBy(() -> service.setMaxPlayers(id, "b", 2)).isInstanceOf(RoomException.class);
        assertThat(service.setMaxPlayers(id, "a", 2).maxPlayers()).isEqualTo(2);
        assertThatThrownBy(() -> service.join(id, "c", "C", "rabbit", null)).hasMessageContaining("full");
        assertThatThrownBy(() -> service.setMaxPlayers(id, "a", 9)).hasMessageContaining("between");
    }

    @Test
    void optionsDefaultFromSpecAndHostCanChangeWithinChoices() {
        var id = service.create("2048").id();
        assertThat(service.find(id).orElseThrow().options()).containsEntry("target", 2048);
        service.join(id, "a", "A", "rabbit", null);
        service.join(id, "b", "B", "rabbit", null);

        assertThat(service.setOptions(id, "a", java.util.Map.of("target", 512)).options()).containsEntry("target", 512);
        assertThat(service.setOptions(id, "a", java.util.Map.of("target", "1024")).options()).containsEntry("target", 1024);
        assertThatThrownBy(() -> service.setOptions(id, "b", java.util.Map.of("target", 512))).hasMessageContaining("host");
        assertThatThrownBy(() -> service.setOptions(id, "a", java.util.Map.of("target", 4096))).hasMessageContaining("invalid option");
        assertThatThrownBy(() -> service.setOptions(id, "a", java.util.Map.of("speed", 1))).hasMessageContaining("invalid option");
    }

    @Test
    void hostLeavingPromotesNextPlayer() {
        var id = service.create("2048").id();
        service.join(id, "a", "A", "rabbit", null);
        service.join(id, "b", "B", "rabbit", null);
        service.leave(id, "a");
        assertThat(service.find(id).orElseThrow().hostId()).isEqualTo("b");
        service.leave(id, "b");
        assertThat(service.find(id)).isEmpty();
    }

    @Test
    void fullMatchLifecycleRanksByScore() {
        var id = service.create("2048").id();
        service.join(id, "a", "A", "rabbit", null);
        service.join(id, "b", "B", "rabbit", null);
        service.join(id, "c", "C", "rabbit", null);

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
        service.join(id, "a", "A", "rabbit", null);
        service.join(id, "b", "B", "rabbit", null);
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
        service.join(id, "a", "A", "rabbit", null);
        service.chat(id, "a", "  hello  ");
        service.chat(id, "a", "   ");
        assertThatThrownBy(() -> service.chat(id, "zzz", "hi")).hasMessageContaining("not in room");
        assertThatThrownBy(() -> service.chat(id, "a", "x".repeat(201))).hasMessageContaining("too long");
        service.join(id, "b", "B", "rabbit", null);
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
        service.join(id, "a", "A", "rabbit", null);
        service.join(id, "b", "B", "rabbit", null);
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
    void playerCanChangeCharacter() {
        var id = service.create("2048").id();
        service.join(id, "a", "A", "rabbit", null);
        var snap = service.setCharacter(id, "a", "tiger");
        assertThat(snap.players().get(0).character()).isEqualTo("tiger");
        assertThatThrownBy(() -> service.setCharacter(id, "zzz", "ox")).hasMessageContaining("not in room");
    }

    @Test
    void hostCanRematchAfterFinishAndStateIsRelayedOnlyWhilePlaying() {
        var id = service.create("stairs").id();
        service.join(id, "a", "A", "rat", null);
        service.join(id, "b", "B", "ox", null);
        service.relayState(id, "a", java.util.Map.of("steps", 1));
        assertThat(relayed).isEmpty();
        service.start(id, "a");
        scheduled.get(0).run();
        service.relayState(id, "a", java.util.Map.of("steps", 3));
        assertThat(relayed).hasSize(1);
        assertThat(relayed.get(0).state()).containsEntry("steps", 3);
        service.finish(id, "a", 10);
        service.finish(id, "b", 20);
        assertThat(service.find(id).orElseThrow().status()).isEqualTo(RoomStatus.FINISHED);
        assertThatThrownBy(() -> service.rematch(id, "b")).hasMessageContaining("host");
        var again = service.rematch(id, "a");
        assertThat(again.status()).isEqualTo(RoomStatus.COUNTDOWN);
        assertThat(again.seed()).isNotZero();
        assertThat(again.players()).allMatch(p -> p.score() == 0 && !p.finished() && p.rank() == null);
        assertThat(again.players()).hasSize(2);
        assertThatThrownBy(() -> service.rematch(id, "a")).hasMessageContaining("not finished");
    }

    @Test
    void turnBasedRoomStartsEngineAndRejectsDuplicateCharacters() {
        var id = service.create("yut").id();
        assertThat(service.find(id).orElseThrow().game().turnBased()).isTrue();
        service.join(id, "a", "A", "rat", null);
        service.join(id, "b", "B", "rat", null);
        assertThatThrownBy(() -> service.start(id, "a")).hasMessageContaining("duplicate");
        service.setCharacter(id, "b", "ox");
        service.setOptions(id, "a", java.util.Map.of("cards", false));
        service.start(id, "a");
        scheduled.get(0).run();
        assertThat(service.find(id).orElseThrow().status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(states).hasSize(1);
        assertThat(states.get(0).view().get("turn")).isEqualTo("a");
        assertThatThrownBy(() -> service.action(id, "b", java.util.Map.of("type", "throw"))).hasMessageContaining("not your turn");
        service.action(id, "a", java.util.Map.of("type", "throw"));
        assertThat(states.size()).isGreaterThanOrEqualTo(2);
        service.leave(id, "b");
        var afterLeave = service.find(id).orElseThrow();
        assertThat(afterLeave.players()).hasSize(2);
        assertThat(afterLeave.players().get(1).connected()).isFalse();
        assertThat(((java.util.List<?>) states.get(states.size() - 1).view().get("players"))).hasSize(2);
        assertThat(botOf(states.get(states.size() - 1), "b")).isTrue();

        service.join(id, "b", "B", "ox", null);
        var afterRejoin = service.find(id).orElseThrow();
        assertThat(afterRejoin.players().get(1).connected()).isTrue();
        assertThat(botOf(states.get(states.size() - 1), "b")).isFalse();
        assertThat(chats).extracting(RoomChatMessage::text).contains("B 님이 다시 들어왔습니다");
        assertThatThrownBy(() -> service.join(id, "c", "C", "tiger", null)).hasMessageContaining("already started");
    }

    @Test
    void disconnectDuringRelayGameKeepsSeatAndRemovesRoomAfterGraceWhenNobodyReturns() {
        var id = service.create("stairs").id();
        service.join(id, "a", "A", "rat", null);
        service.join(id, "b", "B", "ox", null);
        service.start(id, "a");
        scheduled.get(0).run();
        service.leave(id, "a");
        var snap = service.find(id).orElseThrow();
        assertThat(snap.players()).hasSize(2);
        assertThat(snap.hostId()).isEqualTo("b");
        assertThat(snap.players().get(0).connected()).isFalse();
        service.finish(id, "b", 10);
        assertThat(service.find(id).orElseThrow().status()).isEqualTo(RoomStatus.FINISHED);
        service.leave(id, "b");
        assertThat(service.find(id)).isEmpty();
    }

    @Test
    void roomAbandonedMidGameIsRemovedAfterGrace() {
        var id = service.create("stairs").id();
        service.join(id, "a", "A", "rat", null);
        service.start(id, "a");
        scheduled.get(0).run();
        service.leave(id, "a");
        assertThat(service.find(id)).isPresent();
        scheduled.get(scheduled.size() - 1).run();
        assertThat(service.find(id)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static boolean botOf(RoomGameState state, String playerId) {
        var players = (java.util.List<java.util.Map<String, Object>>) state.view().get("players");
        return players.stream().filter(p -> playerId.equals(p.get("id"))).map(p -> (Boolean) p.get("bot")).findFirst().orElseThrow();
    }

    @Test
    void rejectsUnknownGame() {
        assertThatThrownBy(() -> service.create("nope")).hasMessageContaining("unknown game");
    }

    @Test
    void finishedRoomPublishesResultOnceWithUserIds() {
        var id = service.create("2048").id();
        service.join(id, "a", "A", "rat", 7L);
        service.join(id, "b", "B", "ox", null);
        service.start(id, "a");
        scheduled.get(0).run();
        service.finish(id, "a", 100);
        service.finish(id, "b", 300);

        var finished = published.stream().filter(RoomFinished.class::isInstance).map(RoomFinished.class::cast).toList();
        assertThat(finished).hasSize(1);
        var results = finished.get(0).results();
        assertThat(results).extracting(RoomFinished.Result::userId).containsExactly(7L, null);
        assertThat(results).extracting(RoomFinished.Result::rank).containsExactly(2, 1);
        assertThat(finished.get(0).gameId()).isEqualTo("2048");
    }

    @Test
    void inviteRequiresWaitingRoomAndMembership() {
        var id = service.create("2048").id();
        service.join(id, "a", "A", "rat", 7L);

        service.invite(id, 7L, "A", 9L);
        assertThat(published).filteredOn(RoomInvited.class::isInstance).hasSize(1);
        assertThatThrownBy(() -> service.invite(id, 8L, "X", 9L)).hasMessageContaining("not in room");
        assertThatThrownBy(() -> service.invite(id, 7L, "A", 7L)).hasMessageContaining("already in room");
    }
}
