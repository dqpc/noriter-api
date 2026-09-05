package games.noriter.api.game.yut;

import games.noriter.api.game.TurnGame;
import games.noriter.api.game.TurnState;
import games.noriter.api.game.yut.YutRules.Move;
import games.noriter.api.game.yut.YutState.Phase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class YutGame implements TurnGame {

    static final int DEFAULT_PIECES = 3;
    static final int TURN_SECONDS = 30;
    static final int BOT_DELAY_SECONDS = 1;

    @Override public String gameId() { return "yut"; }

    @Override
    public TurnState start(long seed, Map<String, Object> options, List<String> playerIds, Instant now) {
        int pieces = Integer.parseInt(String.valueOf(options.getOrDefault("pieces", DEFAULT_PIECES)));
        var s = new YutState(seed, playerIds, true, pieces, TURN_SECONDS, BOT_DELAY_SECONDS);
        s.deadline = now.plusSeconds(TURN_SECONDS);
        return s;
    }

    @Override
    public TurnState apply(TurnState raw, String playerId, Map<String, Object> action, Instant now) {
        var s = (YutState) raw;
        if (s.ended()) return s;
        if (!s.currentPlayer().equals(playerId)) throw new IllegalArgumentException("not your turn");
        if (s.isBot(playerId)) throw new IllegalArgumentException("bot controlled");
        switch (String.valueOf(action.get("type"))) {
            case "throw" -> {
                if (s.phase != Phase.THROW) throw new IllegalArgumentException("not throw phase");
                doThrow(s, now);
            }
            case "move" -> {
                if (s.phase != Phase.MOVE) throw new IllegalArgumentException("not move phase");
                int pieceId = Integer.parseInt(String.valueOf(action.get("pieceId")));
                var result = Throw.valueOf(String.valueOf(action.get("result")));
                Integer via = action.get("via") == null ? null : Integer.parseInt(String.valueOf(action.get("via")));
                var move = YutRules.legalMoves(s).stream()
                        .filter(m -> m.pieceId() == pieceId && m.result() == result && java.util.Objects.equals(m.via(), via))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("illegal move"));
                doMove(s, move, now);
            }
            default -> throw new IllegalArgumentException("unknown action");
        }
        return s;
    }

    @Override
    public TurnState auto(TurnState raw, Instant now) {
        var s = (YutState) raw;
        if (s.ended()) return s;
        if (s.phase == Phase.THROW) doThrow(s, now);
        else if (s.phase == Phase.MOVE) {
            var moves = YutRules.legalMoves(s);
            if (moves.isEmpty()) endTurn(s, now);
            else doMove(s, YutBot.choose(s, moves), now);
        }
        return s;
    }

    @Override
    public TurnState leave(TurnState raw, String playerId, Instant now) {
        var s = (YutState) raw;
        if (s.ended() || !s.players.contains(playerId)) return s;
        s.bots.add(playerId);
        if (s.currentPlayer().equals(playerId)) s.deadline = now.plusSeconds(s.botDelaySeconds);
        return s;
    }

    @Override
    public TurnState rejoin(TurnState raw, String playerId, Instant now) {
        var s = (YutState) raw;
        if (s.ended() || !s.bots.remove(playerId)) return s;
        if (s.currentPlayer().equals(playerId)) resetDeadline(s, now);
        return s;
    }

    private void doThrow(YutState s, Instant now) {
        var sticks = new boolean[4];
        for (int i = 0; i < 4; i++) sticks[i] = s.rng.nextBoolean();
        var result = Throw.of(sticks, s.backdo);
        s.sticks = sticks;
        s.queue.add(result);
        if (s.bonusThrows > 0) s.bonusThrows--;
        s.lastEvent = Map.of("type", "throw", "player", s.currentPlayer(), "result", result.name());
        if (result.again()) {
            s.phase = Phase.THROW;
        } else if (YutRules.legalMoves(s).isEmpty()) {
            s.lastEvent = Map.of("type", "skip", "player", s.currentPlayer(), "result", result.name());
            endTurn(s, now);
            return;
        } else {
            s.phase = Phase.MOVE;
        }
        resetDeadline(s, now);
    }

    private void doMove(YutState s, Move move, Instant now) {
        var player = s.currentPlayer();
        boolean captured = YutRules.applyMove(s, move);
        s.queue.remove(move.result());
        if (captured) s.bonusThrows++;
        s.lastEvent = Map.of("type", "move", "player", player, "pieceId", move.pieceId(), "result", move.result().name(),
                "dest", move.dest(), "captured", captured, "finished", move.dest() == YutRules.FINISH);
        if (s.finishedCount(player) == s.pieceCount && !s.finishedOrder.contains(player)) {
            s.finishedOrder.add(player);
            s.phase = Phase.ENDED;
            s.deadline = null;
            s.lastEvent = Map.of("type", "end", "winner", player);
            return;
        }
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
        s.turn = (s.turn + 1) % s.players.size();
        s.phase = Phase.THROW;
        resetDeadline(s, now);
    }

    private void resetDeadline(YutState s, Instant now) {
        s.deadline = now.plus(Duration.ofSeconds(s.isBot(s.currentPlayer()) ? s.botDelaySeconds : s.turnSeconds));
    }

}
