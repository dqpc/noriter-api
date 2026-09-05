package games.noriter.api.game.stairs;

import games.noriter.api.game.SharedGame;
import games.noriter.api.game.SharedState;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class StairsShared implements SharedGame {

    private static final int PATTERN_AHEAD = 60;
    private static final int ITEM_MIN_STEP = 5;
    private static final double ITEM_CHANCE = 0.08;
    private static final long ITEM_SEED_MIX = 0x9e3779b9L;

    record Rules(double maxEnergy, double drainPerSec, double gainPerStep, double drainGrowthPerStep) {
        static Rules of(Object speed) {
            return "fast".equals(String.valueOf(speed))
                    ? new Rules(100, 30, 8, 0.08)
                    : new Rules(100, 22, 9, 0.05);
        }
    }

    record State(long seed, Rules rules, Map<String, String> roles, int steps, double energy,
                 Instant energyAt, boolean started, boolean ended, boolean fell) implements SharedState {

        double drainRate() {
            return rules.drainPerSec() * (1 + rules.drainGrowthPerStep() * steps);
        }

        double energyAt(Instant now) {
            if (!started || ended) return energy;
            double dt = Math.max(0, Duration.between(energyAt, now).toMillis()) / 1000.0;
            return Math.max(0, energy - drainRate() * dt);
        }

        @Override public boolean ended() { return ended; }
        @Override public long score() { return steps; }

        @Override
        public Instant deadline() {
            if (!started || ended) return null;
            return energyAt.plusMillis((long) Math.ceil(energy / drainRate() * 1000));
        }

        @Override
        public Map<String, Object> view() {
            var v = new LinkedHashMap<String, Object>();
            v.put("steps", steps);
            v.put("energy", energy);
            v.put("maxEnergy", rules.maxEnergy());
            v.put("drainPerSec", drainRate());
            v.put("started", started);
            v.put("ended", ended);
            v.put("fell", fell);
            v.put("roles", roles);
            v.put("pattern", pattern(seed, steps + PATTERN_AHEAD));
            return v;
        }
    }

    /** 대문자 = 일반 계단, 소문자 = 번개(에너지 회복) 계단 */
    static String pattern(long seed, int upTo) {
        var dirs = new Random(seed);
        var items = new Random(seed ^ ITEM_SEED_MIX);
        var sb = new StringBuilder(upTo + 1).append('R');
        for (int i = 1; i <= upTo; i++) {
            char c = dirs.nextBoolean() ? 'L' : 'R';
            boolean item = i >= ITEM_MIN_STEP && items.nextDouble() < ITEM_CHANCE;
            sb.append(item ? Character.toLowerCase(c) : c);
        }
        return sb.toString();
    }

    static char dirAt(long seed, int i) {
        return Character.toUpperCase(pattern(seed, i).charAt(i));
    }

    static boolean itemAt(long seed, int i) {
        return Character.isLowerCase(pattern(seed, i).charAt(i));
    }

    @Override public String gameId() { return "stairs"; }
    @Override public int players() { return 2; }

    @Override
    public SharedState start(long seed, Map<String, Object> options, List<String> playerIds, Instant now) {
        if (playerIds.size() != players()) throw new IllegalArgumentException("stairs coop needs 2 players");
        var roles = new LinkedHashMap<String, String>();
        roles.put(playerIds.get(0), "L");
        roles.put(playerIds.get(1), "R");
        var rules = Rules.of(options.get("speed"));
        return new State(seed, rules, roles, 0, rules.maxEnergy(), now, false, false, false);
    }

    @Override
    public SharedState apply(SharedState raw, String playerId, Map<String, Object> input, Instant now) {
        var s = (State) raw;
        if (s.ended()) return s;
        var dir = String.valueOf(input.get("dir"));
        if (!dir.equals(s.roles().get(playerId))) return s;
        double energy = s.energyAt(now);
        if (s.started() && energy <= 0) return new State(s.seed(), s.rules(), s.roles(), s.steps(), 0, now, true, true, false);
        if (dirAt(s.seed(), s.steps() + 1) != dir.charAt(0)) {
            return new State(s.seed(), s.rules(), s.roles(), s.steps(), energy, now, true, true, true);
        }
        int next = s.steps() + 1;
        double gained = itemAt(s.seed(), next)
                ? s.rules().maxEnergy()
                : Math.min(s.rules().maxEnergy(), energy + s.rules().gainPerStep());
        return new State(s.seed(), s.rules(), s.roles(), next, gained, now, true, false, false);
    }

    @Override
    public SharedState tick(SharedState raw, Instant now) {
        var s = (State) raw;
        if (s.ended() || !s.started()) return s;
        double energy = s.energyAt(now);
        if (energy <= 0) return new State(s.seed(), s.rules(), s.roles(), s.steps(), 0, now, true, true, false);
        return new State(s.seed(), s.rules(), s.roles(), s.steps(), energy, now, true, false, false);
    }
}
