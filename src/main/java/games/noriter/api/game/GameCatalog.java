package games.noriter.api.game;

import games.noriter.api.config.NoriterProperties;
import games.noriter.api.game.TurnGame;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GameCatalog {

    private final Map<String, GameSpec> specs = new LinkedHashMap<>();
    private final Map<String, TurnGame> turnGames;

    public GameCatalog(NoriterProperties props, List<TurnGame> turnGames) {
        boolean devOptions = props.game() != null && props.game().devOptions();
        List.of(game2048(devOptions), stairs(), yut()).forEach(s -> specs.put(s.id(), s));
        this.turnGames = turnGames.stream().collect(Collectors.toMap(TurnGame::gameId, g -> g));
    }

    public Optional<TurnGame> turnGame(String id) {
        return Optional.ofNullable(turnGames.get(id));
    }

    private static GameSpec game2048(boolean devOptions) {
        var targets = new ArrayList<Object>(List.of(512, 1024, 2048));
        if (devOptions) targets.add(0, 64);
        return new GameSpec("2048", "2048", 1, 4, 8, Duration.ofMinutes(3), true, true,
                Map.of("target", List.copyOf(targets)), Map.of("target", 2048), false, false);
    }

    private static GameSpec stairs() {
        return new GameSpec("stairs", "계단 오르기", 1, 4, 8, null, true, true, Map.of(), Map.of(), false, false);
    }

    private static GameSpec yut() {
        return new GameSpec("yut", "윷놀이", 2, 4, 4, null, true, true,
                Map.of("backdo", List.of(true, false), "pieces", List.of(2, 3, 4)),
                Map.of("backdo", true, "pieces", 3), true, true);
    }

    public Optional<GameSpec> find(String id) {
        return Optional.ofNullable(specs.get(id));
    }

    public GameSpec require(String id) {
        return find(id).orElseThrow(() -> new UnknownGameException(id));
    }

    public Collection<GameSpec> all() {
        return List.copyOf(specs.values());
    }
}
