package games.noriter.api.game.yut;

import games.noriter.api.game.yut.YutRules.Move;
import java.util.List;

final class YutBot {

    static Move choose(YutState s, List<Move> moves) {
        Move best = null;
        int bestValue = Integer.MIN_VALUE;
        for (var m : moves) {
            int v = value(m);
            if (v > bestValue) { bestValue = v; best = m; }
        }
        return best;
    }

    static int value(Move m) {
        int v = 0;
        if (m.captures() > 0) v += 100 + 20 * m.captures();
        if (m.dest() == YutRules.FINISH) v += 80;
        else {
            if (m.dest() == Board.MO || m.dest() == Board.BACK_MO) v += 30;
            if (m.dest() == Board.BANG) v += 25;
            if (m.via() != null && m.via() == 27) v += 10;
            v += m.stacks() > 0 ? -5 : 0;
        }
        v += m.result().steps;
        return v;
    }

    private YutBot() {}
}
