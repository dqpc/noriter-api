package games.noriter.api.game.yut;

import games.noriter.api.game.TurnGame;
import games.noriter.api.game.TurnState;
import games.noriter.api.game.yut.Board.Path;
import games.noriter.api.game.yut.YutRules.Move;
import games.noriter.api.game.yut.YutRules.MoveResult;
import games.noriter.api.game.yut.YutState.CardDraw;
import games.noriter.api.game.yut.YutState.CardTrigger;
import games.noriter.api.game.yut.YutState.CapturedPiece;
import games.noriter.api.game.yut.YutState.Effects;
import games.noriter.api.game.yut.YutState.Phase;
import games.noriter.api.game.yut.YutState.Piece;
import games.noriter.api.game.yut.YutState.Rolled;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class YutGame implements TurnGame {

    static final int DEFAULT_PIECES = 3;
    static final int TURN_SECONDS = 30;
    static final int CARD_SECONDS = 15;
    static final int BOT_DELAY_SECONDS = 1;
    static final int ANGELS_PER_PILE = 4;
    static final int DEVILS_PER_PILE = 1;

    @Override public String gameId() { return "yut"; }

    @Override
    public TurnState start(long seed, Map<String, Object> options, List<String> playerIds, Instant now) {
        int pieces = Integer.parseInt(String.valueOf(options.getOrDefault("pieces", DEFAULT_PIECES)));
        boolean cards = !"false".equals(String.valueOf(options.getOrDefault("cards", "true")));
        var s = new YutState(seed, playerIds, true, cards, pieces, TURN_SECONDS, CARD_SECONDS, BOT_DELAY_SECONDS);
        if (cards) {
            s.opening = true;
            s.openingQueue.addAll(playerIds.subList(1, playerIds.size()));
            beginCard(s, playerIds.get(0), CardTrigger.OPENING, null, List.of(), 0, now);
        } else {
            beginTurn(s, now);
        }
        return s;
    }

    @Override
    public TurnState apply(TurnState raw, String playerId, Map<String, Object> action, Instant now) {
        var s = (YutState) raw;
        if (s.ended()) return s;
        if (s.resigned.contains(playerId)) throw new IllegalArgumentException("resigned");
        if ("surrender".equals(String.valueOf(action.get("type")))) {
            if (!s.players.contains(playerId)) throw new IllegalArgumentException("not in game");
            surrender(s, playerId, now);
            return s;
        }
        if (!s.actor().equals(playerId)) throw new IllegalArgumentException("not your turn");
        if (s.isBot(playerId)) throw new IllegalArgumentException("bot controlled");
        switch (String.valueOf(action.get("type"))) {
            case "throw" -> {
                if (s.phase != Phase.THROW) throw new IllegalArgumentException("not throw phase");
                Throw chosen = action.get("result") == null ? null : Throw.valueOf(String.valueOf(action.get("result")));
                if (s.effects(playerId).chooseThrow && !s.effects(playerId).forcedBackdo && chosen == null) {
                    throw new IllegalArgumentException("choose a result");
                }
                doThrow(s, now, chosen);
            }
            case "move" -> {
                if (s.phase != Phase.MOVE) throw new IllegalArgumentException("not move phase");
                int pieceId = Integer.parseInt(String.valueOf(action.get("pieceId")));
                var result = Throw.valueOf(String.valueOf(action.get("result")));
                int steps = action.get("steps") == null ? result.steps : Integer.parseInt(String.valueOf(action.get("steps")));
                Integer via = action.get("via") == null ? null : Integer.parseInt(String.valueOf(action.get("via")));
                var move = YutRules.legalMoves(s).stream()
                        .filter(m -> m.matches(pieceId, result, steps, via))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("illegal move"));
                doMove(s, move, now);
            }
            case "card" -> {
                if (s.phase != Phase.CARD || s.cardDraw == null) throw new IllegalArgumentException("not card phase");
                int index = Integer.parseInt(String.valueOf(action.get("index")));
                if (index < 0 || index >= s.cardDraw.pile.size()) throw new IllegalArgumentException("no such card");
                pickCard(s, index, now);
            }
            default -> throw new IllegalArgumentException("unknown action");
        }
        return s;
    }

    @Override
    public TurnState auto(TurnState raw, Instant now) {
        var s = (YutState) raw;
        if (s.ended()) return s;
        switch (s.phase) {
            case THROW -> doThrow(s, now, s.effects(s.currentPlayer()).chooseThrow ? Throw.MO : null);
            case MOVE -> {
                var moves = YutRules.legalMoves(s);
                if (moves.isEmpty()) { s.queue.clear(); endTurnOrBonus(s, now); }
                else doMove(s, YutBot.choose(s, moves), now);
            }
            case CARD -> pickCard(s, s.rng.nextInt(s.cardDraw.pile.size()), now);
            case ENDED -> { }
        }
        return s;
    }

    @Override
    public TurnState leave(TurnState raw, String playerId, Instant now) {
        var s = (YutState) raw;
        if (s.ended() || !s.players.contains(playerId)) return s;
        s.bots.add(playerId);
        if (s.actor().equals(playerId)) s.deadline = now.plusSeconds(s.botDelaySeconds);
        return s;
    }

    @Override
    public TurnState rejoin(TurnState raw, String playerId, Instant now) {
        var s = (YutState) raw;
        if (s.ended() || !s.bots.remove(playerId)) return s;
        if (s.actor().equals(playerId)) resetDeadline(s, now);
        return s;
    }

    /** 항복: 말을 모두 거두고 순위 맨 아래로. 남은 사람이 하나면 게임 종료. */
    private void surrender(YutState s, String playerId, Instant now) {
        s.resigned.add(playerId);
        for (var p : s.pieces.get(playerId)) p.home();
        s.openingQueue.remove(playerId);
        s.lastEvent = Map.of("type", "surrender", "player", playerId);
        var active = s.activePlayers();
        if (active.size() <= 1) {
            s.phase = Phase.ENDED;
            s.deadline = null;
            s.cardDraw = null;
            s.lastEvent = Map.of("type", "end", "winner", active.isEmpty() ? playerId : active.get(0), "bySurrender", true);
            return;
        }
        boolean wasActor = s.actor().equals(playerId);
        if (s.opening) {
            if (!wasActor) return;
            s.cardDraw = null;
            if (!s.openingQueue.isEmpty()) {
                beginCard(s, s.openingQueue.remove(0), CardTrigger.OPENING, null, List.of(), 0, now);
            } else {
                s.opening = false;
                beginTurn(s, now);
            }
            return;
        }
        if (wasActor || s.currentPlayer().equals(playerId)) {
            s.cardDraw = null;
            s.resumeAfterMove = false;
            endTurn(s, now);
        }
    }

    private void doThrow(YutState s, Instant now, Throw chosen) {
        var player = s.currentPlayer();
        var e = s.effects(player);
        Throw result;
        boolean[] sticks;
        boolean chosenFlag = false;
        if (e.forcedBackdo) {
            result = Throw.BACKDO;
            sticks = sticksFor(result);
            e.forcedBackdo = false;
        } else if (e.chooseThrow && chosen != null) {
            result = chosen;
            sticks = sticksFor(result);
            e.chooseThrow = false;
            chosenFlag = true;
        } else {
            sticks = new boolean[4];
            for (int i = 0; i < 4; i++) sticks[i] = s.rng.nextBoolean();
            result = Throw.of(sticks, s.backdo);
        }
        boolean converted = false;
        if (result == Throw.BACKDO && e.backdoImmune()) { result = Throw.DO; converted = true; }
        int steps = result.steps;
        boolean boosted = false;
        if (e.stepBonus && steps > 0) { steps++; e.stepBonus = false; boosted = true; }
        s.sticks = sticks;
        s.queue.add(new Rolled(result, steps));
        boolean free = s.freeThrow;
        s.freeThrow = false;
        if (!free && s.bonusThrows > 0) s.bonusThrows--;
        if (e.extraThrows > 0) { s.bonusThrows += e.extraThrows; e.extraThrows = 0; }
        var ev = new LinkedHashMap<String, Object>();
        ev.put("type", "throw");
        ev.put("seq", ++s.throwSeq);
        ev.put("player", player);
        ev.put("result", result.name());
        ev.put("steps", steps);
        ev.put("chosen", chosenFlag);
        ev.put("converted", converted);
        ev.put("boosted", boosted);
        s.lastEvent = ev;
        if (result.again() && !chosenFlag) {
            s.phase = Phase.THROW;
            s.freeThrow = true;
        } else if (YutRules.legalMoves(s).isEmpty()) {
            s.lastEvent = Map.of("type", "skip", "player", player, "result", result.name());
            s.queue.clear();
            endTurnOrBonus(s, now);
            return;
        } else {
            s.phase = Phase.MOVE;
        }
        resetDeadline(s, now);
    }

    static boolean[] sticksFor(Throw t) {
        return switch (t) {
            case BACKDO -> new boolean[]{true, false, false, false};
            case DO -> new boolean[]{false, true, false, false};
            case GAE -> new boolean[]{true, true, false, false};
            case GEOL -> new boolean[]{true, true, true, false};
            case YUT -> new boolean[]{true, true, true, true};
            case MO -> new boolean[]{false, false, false, false};
        };
    }

    private void doMove(YutState s, Move move, Instant now) {
        var player = s.currentPlayer();
        var mover = s.piece(player, move.pieceId());
        boolean usedShortcut = move.via() != null && mover.onBoard() && !YutRules.isBangStop(mover);
        var res = YutRules.applyMove(s, player, move);
        s.queue.remove(new Rolled(move.result(), move.steps()));
        if (usedShortcut) s.effects(player).shortcutOpen = false;
        int bonus = captureBonus(s, res);
        s.bonusThrows += bonus;
        var ev = new LinkedHashMap<String, Object>();
        ev.put("type", "move");
        ev.put("player", player);
        ev.put("pieceId", move.pieceId());
        ev.put("result", move.result().name());
        ev.put("dest", move.dest());
        ev.put("captured", res.capturedAny());
        ev.put("blocked", move.blocked() > 0);
        ev.put("finished", move.dest() == YutRules.FINISH);
        s.lastEvent = ev;
        if (checkEnd(s, player)) return;
        if (s.cards && (res.capturedAny() || move.dest() == Board.BANG)) {
            s.resumeAfterMove = true;
            beginCard(s, player, res.capturedAny() ? CardTrigger.CAPTURE : CardTrigger.BANG, move.pieceId(), res.captured(), bonus, now);
            return;
        }
        continueAfterMove(s, now);
    }

    private int captureBonus(YutState s, MoveResult res) {
        if (!res.capturedAny()) return 0;
        boolean target = res.captured().stream().anyMatch(c -> s.effects(c.owner()).target());
        return target ? 2 : 1;
    }

    private boolean checkEnd(YutState s, String player) {
        if (s.finishedCount(player) != s.pieceCount || s.finishedOrder.contains(player)) return false;
        s.finishedOrder.add(player);
        s.phase = Phase.ENDED;
        s.deadline = null;
        s.cardDraw = null;
        s.lastEvent = Map.of("type", "end", "winner", player);
        return true;
    }

    private void continueAfterMove(YutState s, Instant now) {
        if (!s.queue.isEmpty()) {
            if (YutRules.legalMoves(s).isEmpty()) { s.queue.clear(); endTurnOrBonus(s, now); return; }
            s.phase = Phase.MOVE;
            resetDeadline(s, now);
            return;
        }
        endTurnOrBonus(s, now);
    }

    private void endTurnOrBonus(YutState s, Instant now) {
        if (s.bonusThrows > 0) {
            s.phase = Phase.THROW;
            resetDeadline(s, now);
        } else {
            endTurn(s, now);
        }
    }

    private void endTurn(YutState s, Instant now) {
        s.queue.clear();
        s.bonusThrows = 0;
        s.freeThrow = false;
        s.turn = nextActive(s, s.turn);
        beginTurn(s, now);
    }

    private static int nextActive(YutState s, int from) {
        int t = from;
        for (int i = 0; i < s.players.size(); i++) {
            t = (t + 1) % s.players.size();
            if (!s.resigned.contains(s.players.get(t))) return t;
        }
        return from;
    }

    /** 차례 시작. 쉬어! 가 걸린 사람은 건너뛴다. 실제로 시작한 차례만 turnNo 를 올린다. */
    private void beginTurn(YutState s, Instant now) {
        if (s.resigned.contains(s.currentPlayer())) s.turn = nextActive(s, s.turn);
        for (int guard = 0; guard < s.players.size(); guard++) {
            var e = s.effects(s.currentPlayer());
            if (e.skipNext) {
                e.skipNext = false;
                s.lastEvent = Map.of("type", "skipTurn", "player", s.currentPlayer());
                s.turn = nextActive(s, s.turn);
                continue;
            }
            e.turnNo++;
            break;
        }
        s.phase = Phase.THROW;
        resetDeadline(s, now);
    }

    private void beginCard(YutState s, String player, CardTrigger trigger, Integer moverId, List<CapturedPiece> captured, int captureBonus, Instant now) {
        var pile = buildPile(s, player, trigger, moverId, captured, captureBonus);
        s.cardDraw = new CardDraw(player, trigger, pile, moverId, captured, captureBonus);
        s.phase = Phase.CARD;
        resetDeadline(s, now);
    }

    List<Card> buildPile(YutState s, String player, CardTrigger trigger, Integer moverId, List<CapturedPiece> captured, int captureBonus) {
        var mover = moverId == null ? null : s.piece(player, moverId);
        var angels = new ArrayList<Card>();
        var devils = new ArrayList<Card>();
        for (var c : Card.values()) {
            if (!eligible(s, player, c, trigger, mover, captured, captureBonus)) continue;
            (c.kind == Card.Kind.ANGEL ? angels : devils).add(c);
        }
        Collections.shuffle(angels, s.rng);
        Collections.shuffle(devils, s.rng);
        var pile = new ArrayList<Card>(angels.subList(0, Math.min(ANGELS_PER_PILE, angels.size())));
        pile.addAll(devils.subList(0, Math.min(DEVILS_PER_PILE, devils.size())));
        Collections.shuffle(pile, s.rng);
        return pile;
    }

    static boolean eligible(YutState s, String player, Card card, CardTrigger trigger, Piece mover, List<CapturedPiece> captured, int captureBonus) {
        var mine = s.pieces.get(player);
        var e = s.effects(player);
        boolean moverOnBoard = mover != null && mover.onBoard();
        boolean anyOnBoard = mine.stream().anyMatch(Piece::onBoard);
        // 켜짐/꺼짐 효과는 이미 걸려 있으면 더미에서 뺀다 (겹쳐도 의미가 없으므로). 한 번 더는 횟수가 쌓이니 항상 가능
        return switch (card) {
            case ONE_MORE -> true;
            case CHOOSE_THROW -> !e.chooseThrow;
            case BACKDO_IMMUNE -> !e.backdoImmune();
            case SHORTCUT -> !e.shortcutOpen;
            case GREED -> !e.stepBonus;
            case CURSED_BACKDO -> !e.forcedBackdo;
            case REST -> !e.skipNext;
            case ONE_STEP -> moverOnBoard;
            case NEW_PIECE -> mine.stream().anyMatch(Piece::waiting);
            case STACK_UP -> moverOnBoard && stackUpCandidate(s, player, mover) != null;
            case SHIELD -> anyOnBoard && !e.shield();
            case TARGET -> anyOnBoard && !e.target();
            case FORFEIT -> captureBonus > 0;
            case RELEASE -> !captured.isEmpty();
            case STEP_BACK -> moverOnBoard && YutRules.back(mover) != null;
            case SCATTER -> scatterStack(s, player, mover) != null;
        };
    }

    private static Piece stackUpCandidate(YutState s, String player, Piece mover) {
        Piece best = null;
        for (var p : s.pieces.get(player)) {
            if (!p.onBoard() || p == mover || p.path != mover.path || p.index >= mover.index) continue;
            if (best == null || p.index > best.index) best = p;
        }
        return best;
    }

    /** 흩어질 무리: 잡은 말이 무리면 그 무리, 아니면 가장 큰 무리. 없으면 null. */
    private static List<Piece> scatterStack(YutState s, String player, Piece mover) {
        if (mover != null && mover.onBoard()) {
            var stack = s.stackOf(mover);
            if (stack.size() >= 2) return stack;
        }
        List<Piece> best = null;
        for (var p : s.pieces.get(player)) {
            if (!p.onBoard()) continue;
            var stack = s.stackOf(p);
            if (stack.size() >= 2 && (best == null || stack.size() > best.size())) best = stack;
        }
        return best;
    }

    private void pickCard(YutState s, int index, Instant now) {
        var draw = s.cardDraw;
        var card = draw.pile.get(index);
        var detail = applyCard(s, draw, card);
        var ev = new LinkedHashMap<String, Object>();
        ev.put("type", "card");
        ev.put("player", draw.player);
        ev.put("trigger", draw.trigger.name());
        ev.put("index", index);
        ev.put("card", card.view());
        ev.putAll(detail);
        s.lastEvent = ev;
        s.cardDraw = null;
        if (s.ended()) return;
        if (draw.trigger == CardTrigger.OPENING) {
            if (!s.openingQueue.isEmpty()) {
                beginCard(s, s.openingQueue.remove(0), CardTrigger.OPENING, null, List.of(), 0, now);
            } else {
                s.opening = false;
                beginTurn(s, now);
            }
            return;
        }
        if (s.resumeAfterMove) {
            s.resumeAfterMove = false;
            continueAfterMove(s, now);
        }
    }

    private Map<String, Object> applyCard(YutState s, CardDraw draw, Card card) {
        var player = draw.player;
        var e = s.effects(player);
        var mover = draw.moverPieceId == null ? null : s.piece(player, draw.moverPieceId);
        boolean opening = draw.trigger == CardTrigger.OPENING;
        var detail = new LinkedHashMap<String, Object>();
        switch (card) {
            case ONE_MORE -> { if (opening) e.extraThrows++; else s.bonusThrows++; }
            case CHOOSE_THROW -> e.chooseThrow = true;
            case ONE_STEP -> effectMove(s, player, mover, YutRules.advance(mover.path, mover.index, 1), 1, false, opening, detail);
            case NEW_PIECE -> {
                var piece = s.pieces.get(player).stream().filter(Piece::waiting).findFirst().orElseThrow();
                effectMove(s, player, piece, YutRules.advance(Path.RING, 0, 1), 1, false, opening, detail);
            }
            case STACK_UP -> {
                var candidate = stackUpCandidate(s, player, mover);
                effectMove(s, player, candidate, new int[]{mover.path.ordinal(), mover.index}, 0, false, opening, detail);
            }
            case SHIELD -> e.shieldUntil = e.turnNo + 1;
            case BACKDO_IMMUNE -> e.backdoImmuneUntil = e.turnNo + 1;
            case SHORTCUT -> e.shortcutOpen = true;
            case GREED -> e.stepBonus = true;
            case FORFEIT -> s.bonusThrows = Math.max(0, s.bonusThrows - draw.captureBonus);
            case RELEASE -> {
                for (var c : draw.captured) {
                    var p = s.piece(c.owner(), c.pieceId());
                    p.path = c.path();
                    p.index = c.index();
                    p.prevPath = c.prevPath();
                    p.prevIndex = c.prevIndex();
                    p.finished = false;
                }
                s.bonusThrows = Math.max(0, s.bonusThrows - draw.captureBonus);
                detail.put("released", draw.captured.stream().map(c -> Map.of("player", c.owner(), "pieceId", c.pieceId())).toList());
            }
            case STEP_BACK -> effectMove(s, player, mover, YutRules.back(mover), -1, false, opening, detail);
            case CURSED_BACKDO -> e.forcedBackdo = true;
            case REST -> e.skipNext = true;
            case TARGET -> e.targetUntil = e.turnNo + 1;
            case SCATTER -> {
                var stack = scatterStack(s, player, mover);
                var top = stack.get(stack.size() - 1);
                effectMove(s, player, top, YutRules.back(top), -1, true, opening, detail);
            }
        }
        return detail;
    }

    /** 카드가 일으킨 이동. 일반 착지 규칙(잡기·업기)을 따르되 카드는 다시 주지 않는다. */
    private void effectMove(YutState s, String player, Piece piece, int[] target, int steps, boolean single, boolean opening, Map<String, Object> detail) {
        if (target == null) return;
        var mv = YutRules.synthetic(s, player, piece, target, steps);
        var res = YutRules.applyMove(s, player, mv, single);
        int bonus = captureBonus(s, res);
        if (opening) s.effects(player).extraThrows += bonus; else s.bonusThrows += bonus;
        detail.put("pieceId", piece.id);
        detail.put("dest", mv.dest());
        detail.put("captured", res.capturedAny());
        checkEnd(s, player);
    }

    private void resetDeadline(YutState s, Instant now) {
        int seconds = s.isBot(s.actor()) ? s.botDelaySeconds : s.phase == Phase.CARD ? s.cardSeconds : s.turnSeconds;
        s.deadline = now.plus(Duration.ofSeconds(seconds));
    }
}
