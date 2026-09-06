package games.noriter.api.game.yut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.noriter.api.game.yut.Board.Path;
import games.noriter.api.game.yut.YutState.CardDraw;
import games.noriter.api.game.yut.YutState.CardTrigger;
import games.noriter.api.game.yut.YutState.Phase;
import games.noriter.api.game.yut.YutState.Piece;
import games.noriter.api.game.yut.YutState.Rolled;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YutCardTests {

    final YutGame game = new YutGame();
    final Instant t0 = Instant.parse("2026-09-06T00:00:00Z");
    YutState s;

    @BeforeEach
    void setUp() {
        s = new YutState(5, List.of("a", "b"), true, true, 3, YutGame.TURN_SECONDS, YutGame.CARD_SECONDS, YutGame.BOT_DELAY_SECONDS);
        s.effects("a").turnNo = 1;
        s.deadline = t0.plusSeconds(YutGame.TURN_SECONDS);
    }

    Piece piece(String player, int id) { return s.piece(player, id); }

    void place(Piece p, Path path, int index) { p.path = path; p.index = index; p.finished = false; }

    /** 카드 한 장짜리 더미를 놓고 바로 뽑는다. */
    void draw(String player, Card card, CardTrigger trigger, Integer moverId, List<YutState.CapturedPiece> captured, int bonus) {
        s.cardDraw = new CardDraw(player, trigger, List.of(card), moverId, captured, bonus);
        s.phase = Phase.CARD;
        game.apply(s, player, Map.of("type", "card", "index", 0), t0);
    }

    void moveTurnTo(String player) {
        s.turn = s.players.indexOf(player);
        s.phase = Phase.THROW;
        s.queue.clear();
        s.bonusThrows = 0;
    }

    @Test
    void openingDealsOneCardToEachPlayerBeforeFirstTurn() {
        var o = (YutState) game.start(9, Map.of("pieces", 2), List.of("a", "b", "c"), t0);
        assertThat(o.phase).isEqualTo(Phase.CARD);
        assertThat(o.actor()).isEqualTo("a");
        assertThat(o.cardDraw.pile).hasSize(5);
        assertThat(o.cardDraw.pile.stream().filter(c -> c.kind == Card.Kind.ANGEL).count()).isEqualTo(4);
        assertThat(o.cardDraw.pile.stream().filter(c -> c.kind == Card.Kind.DEVIL).count()).isEqualTo(1);
        assertThatThrownBy(() -> game.apply(o, "b", Map.of("type", "card", "index", 0), t0)).hasMessageContaining("not your turn");
        assertThat(o.deadline()).isEqualTo(t0.plusSeconds(YutGame.CARD_SECONDS));

        game.apply(o, "a", Map.of("type", "card", "index", 0), t0);
        assertThat(o.actor()).isEqualTo("b");
        game.apply(o, "b", Map.of("type", "card", "index", 1), t0);
        game.apply(o, "c", Map.of("type", "card", "index", 2), t0);
        assertThat(o.phase).isEqualTo(Phase.THROW);
        assertThat(o.currentPlayer()).isEqualTo("a");
        assertThat(o.view().get("card")).isNull();
    }

    @Test
    void captureAndBangLandingTriggerCardThenResume() {
        place(piece("b", 0), Path.RING, 3);
        s.phase = Phase.MOVE;
        s.queue.add(Rolled.of(Throw.GEOL));
        game.apply(s, "a", Map.of("type", "move", "pieceId", 0, "result", "GEOL"), t0);
        assertThat(s.phase).isEqualTo(Phase.CARD);
        assertThat(s.cardDraw.trigger).isEqualTo(CardTrigger.CAPTURE);
        assertThat(s.cardDraw.captureBonus).isEqualTo(1);
        assertThat(s.bonusThrows).isEqualTo(1);
        assertThat(s.cardDraw.pile).contains(Card.FORFEIT).hasSize(5);

        game.apply(s, "a", Map.of("type", "card", "index", 0), t0);
        assertThat(s.phase).isIn(Phase.THROW, Phase.MOVE);
        assertThat(s.currentPlayer()).isEqualTo("a");

        moveTurnTo("a");
        place(piece("a", 1), Path.A, 1);
        s.phase = Phase.MOVE;
        s.queue.add(Rolled.of(Throw.GAE));
        game.apply(s, "a", Map.of("type", "move", "pieceId", 1, "result", "GAE"), t0);
        assertThat(piece("a", 1).node()).isEqualTo(Board.BANG);
        assertThat(s.phase).isEqualTo(Phase.CARD);
        assertThat(s.cardDraw.trigger).isEqualTo(CardTrigger.BANG);
        assertThat(s.cardDraw.pile).doesNotContain(Card.FORFEIT, Card.RELEASE);
    }

    @Test
    void pileOnlyContainsCardsWhosePreconditionHolds() {
        var mover = piece("a", 0);
        place(mover, Path.RING, 6);
        assertThat(YutGame.eligible(s, "a", Card.STACK_UP, CardTrigger.BANG, mover, List.of(), 0)).isFalse();
        place(piece("a", 1), Path.RING, 2);
        assertThat(YutGame.eligible(s, "a", Card.STACK_UP, CardTrigger.BANG, mover, List.of(), 0)).isTrue();
        assertThat(YutGame.eligible(s, "a", Card.SCATTER, CardTrigger.BANG, mover, List.of(), 0)).isFalse();
        assertThat(YutGame.eligible(s, "a", Card.NEW_PIECE, CardTrigger.OPENING, null, List.of(), 0)).isTrue();
        place(piece("a", 2), Path.RING, 6);
        assertThat(YutGame.eligible(s, "a", Card.SCATTER, CardTrigger.BANG, mover, List.of(), 0)).isTrue();
        assertThat(YutGame.eligible(s, "a", Card.NEW_PIECE, CardTrigger.OPENING, null, List.of(), 0)).isFalse();
        assertThat(YutGame.eligible(s, "a", Card.SHIELD, CardTrigger.OPENING, null, List.of(), 0)).isTrue();
        var fresh = (YutState) game.start(1, Map.of("cards", false), List.of("x", "y"), t0);
        assertThat(YutGame.eligible(fresh, "x", Card.SHIELD, CardTrigger.OPENING, null, List.of(), 0)).isFalse();
        assertThat(YutGame.eligible(fresh, "x", Card.ONE_STEP, CardTrigger.OPENING, null, List.of(), 0)).isFalse();
    }

    @Test
    void oneMoreAddsBonusAndOneStepAdvancesWithNormalLanding() {
        s.bonusThrows = 1;
        draw("a", Card.ONE_MORE, CardTrigger.CAPTURE, 0, List.of(), 1);
        assertThat(s.bonusThrows).isEqualTo(2);

        var mover = piece("a", 0);
        place(mover, Path.RING, 4);
        place(piece("b", 0), Path.A, 0);
        s.bonusThrows = 0;
        draw("a", Card.ONE_STEP, CardTrigger.BANG, 0, List.of(), 0);
        assertThat(mover.path).isEqualTo(Path.A);
        assertThat(mover.index).isEqualTo(0);
        assertThat(piece("b", 0).waiting()).isTrue();
        assertThat(s.bonusThrows).isEqualTo(1);
        assertThat(s.lastEvent.get("captured")).isEqualTo(true);
    }

    @Test
    void shieldBlocksCaptureAndCoexists() {
        place(piece("a", 0), Path.RING, 3);
        s.effects("a").shieldUntil = s.effects("a").turnNo + 1;
        moveTurnTo("b");
        s.phase = Phase.MOVE;
        s.queue.add(Rolled.of(Throw.GEOL));
        var mv = YutRules.legalMoves(s).stream().filter(m -> m.dest() == 3).findFirst().orElseThrow();
        assertThat(mv.captures()).isZero();
        assertThat(mv.blocked()).isEqualTo(1);
        game.apply(s, "b", Map.of("type", "move", "pieceId", 0, "result", "GEOL"), t0);
        assertThat(piece("a", 0).node()).isEqualTo(3);
        assertThat(piece("b", 0).node()).isEqualTo(3);
        assertThat(s.bonusThrows).isZero();
        assertThat(s.currentPlayer()).isEqualTo("a");
        assertThat(s.effects("a").shield()).isFalse();
    }

    @Test
    void chooseThrowGreedAndCurseChangeTheNextThrow() {
        s.effects("a").chooseThrow = true;
        s.effects("a").stepBonus = true;
        assertThatThrownBy(() -> game.apply(s, "a", Map.of("type", "throw"), t0)).hasMessageContaining("choose");
        game.apply(s, "a", Map.of("type", "throw", "result", "MO"), t0);
        assertThat(s.queue).containsExactly(new Rolled(Throw.MO, 6));
        assertThat(s.phase).isEqualTo(Phase.MOVE);
        assertThat(s.lastEvent.get("chosen")).isEqualTo(true);

        moveTurnTo("b");
        place(piece("b", 0), Path.RING, 7);
        s.effects("b").forcedBackdo = true;
        game.apply(s, "b", Map.of("type", "throw"), t0);
        assertThat(s.queue).containsExactly(Rolled.of(Throw.BACKDO));

        moveTurnTo("a");
        s.effects("a").forcedBackdo = true;
        s.effects("a").backdoImmuneUntil = s.effects("a").turnNo;
        game.apply(s, "a", Map.of("type", "throw"), t0);
        assertThat(s.queue).containsExactly(Rolled.of(Throw.DO));
        assertThat(s.lastEvent.get("converted")).isEqualTo(true);
    }

    @Test
    void yutOrMoExtraThrowDoesNotConsumeBonusThrows() {
        s.bonusThrows = 2;
        s.freeThrow = true;
        s.effects("a").chooseThrow = true;
        game.apply(s, "a", Map.of("type", "throw", "result", "GAE"), t0);
        assertThat(s.bonusThrows).isEqualTo(2);
        assertThat(s.freeThrow).isFalse();

        s.phase = Phase.THROW;
        s.queue.clear();
        s.effects("a").chooseThrow = true;
        game.apply(s, "a", Map.of("type", "throw", "result", "DO"), t0);
        assertThat(s.bonusThrows).isEqualTo(1);
    }

    @Test
    void restSkipsNextTurnAndClears() {
        s.effects("b").skipNext = true;
        s.phase = Phase.MOVE;
        s.queue.add(Rolled.of(Throw.DO));
        game.apply(s, "a", Map.of("type", "move", "pieceId", 0, "result", "DO"), t0);
        assertThat(s.currentPlayer()).isEqualTo("a");
        assertThat(s.lastEvent.get("type")).isEqualTo("skipTurn");
        assertThat(s.effects("b").skipNext).isFalse();
    }

    @Test
    void shortcutOpenOffersBranchWhilePassingAndIsConsumed() {
        var mover = piece("a", 0);
        place(mover, Path.RING, 3);
        s.effects("a").shortcutOpen = true;
        s.phase = Phase.MOVE;
        s.queue.add(Rolled.of(Throw.MO));
        var moves = YutRules.legalMoves(s).stream().filter(m -> m.pieceId() == 0).toList();
        assertThat(moves).extracting(YutRules.Move::dest).containsExactlyInAnyOrder(8, Board.BANG);
        var alt = moves.stream().filter(m -> m.via() != null).findFirst().orElseThrow();
        assertThat(alt.via()).isEqualTo(20);
        game.apply(s, "a", Map.of("type", "move", "pieceId", 0, "result", "MO", "via", 20), t0);
        assertThat(mover.node()).isEqualTo(Board.BANG);
        assertThat(s.effects("a").shortcutOpen).isFalse();
    }

    @Test
    void releaseRestoresCapturedPiecesAndRevokesBonus() {
        var victim = piece("b", 0);
        place(victim, Path.RING, 4);
        place(piece("a", 0), Path.RING, 2);
        s.phase = Phase.MOVE;
        s.queue.add(Rolled.of(Throw.GAE));
        game.apply(s, "a", Map.of("type", "move", "pieceId", 0, "result", "GAE"), t0);
        assertThat(victim.waiting()).isTrue();
        var captured = s.cardDraw.captured;
        s.cardDraw = new CardDraw("a", CardTrigger.CAPTURE, List.of(Card.RELEASE), 0, captured, 1);
        game.apply(s, "a", Map.of("type", "card", "index", 0), t0);
        assertThat(victim.node()).isEqualTo(4);
        assertThat(piece("a", 0).node()).isEqualTo(4);
        assertThat(s.bonusThrows).isZero();
        assertThat(s.currentPlayer()).isEqualTo("b");
    }

    @Test
    void scatterDropsOnlyTheTopPieceAndTargetDoublesBonus() {
        place(piece("a", 0), Path.RING, 6);
        place(piece("a", 1), Path.RING, 6);
        draw("a", Card.SCATTER, CardTrigger.BANG, 0, List.of(), 0);
        assertThat(piece("a", 0).node()).isEqualTo(6);
        assertThat(piece("a", 1).node()).isEqualTo(5);

        s.effects("a").targetUntil = s.effects("a").turnNo + 1;
        moveTurnTo("b");
        place(piece("b", 0), Path.RING, 4);
        s.phase = Phase.MOVE;
        s.queue.add(Rolled.of(Throw.GAE));
        game.apply(s, "b", Map.of("type", "move", "pieceId", 0, "result", "GAE"), t0);
        assertThat(s.bonusThrows).isEqualTo(2);
    }

    @Test
    void surrenderRemovesPlayerAndEndsGameWhenOneRemains() {
        var o = (YutState) game.start(3, Map.of("pieces", 2, "cards", false), List.of("a", "b", "c"), t0);
        place(o.piece("b", 0), Path.RING, 4);
        game.apply(o, "b", Map.of("type", "surrender"), t0);
        assertThat(o.resigned).containsExactly("b");
        assertThat(o.piece("b", 0).onBoard()).isFalse();
        assertThat(o.currentPlayer()).isEqualTo("a");
        assertThat(o.ranking()).endsWith("b");
        assertThatThrownBy(() -> game.apply(o, "b", Map.of("type", "throw"), t0)).hasMessageContaining("resigned");
        game.apply(o, "a", Map.of("type", "throw"), t0);
        if (o.currentPlayer().equals("b")) throw new AssertionError("resigned player got a turn");
        game.apply(o, "c", Map.of("type", "surrender"), t0);
        assertThat(o.ended()).isTrue();
        assertThat(o.ranking()).containsExactly("a", "c", "b");
        assertThat(o.scores().get("a")).isEqualTo(3L);
    }

    @Test
    void botsDrawCardsAndFinishAGameWithCardsOn() {
        var o = (YutState) game.start(21, Map.of("pieces", 2), List.of("a", "b", "c"), t0);
        game.leave(o, "a", t0);
        game.leave(o, "b", t0);
        game.leave(o, "c", t0);
        var now = t0;
        int steps = 0;
        while (!o.ended() && steps++ < 20000) {
            now = o.deadline();
            game.auto(o, now);
        }
        assertThat(o.ended()).isTrue();
        assertThat(o.finishedOrder).hasSize(1);
    }
}
