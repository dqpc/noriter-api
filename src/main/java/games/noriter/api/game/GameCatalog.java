package games.noriter.api.game;

import games.noriter.api.config.NoriterProperties;
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
    private final Map<String, SharedGame> sharedGames;

    public GameCatalog(NoriterProperties props, List<SharedGame> sharedGames) {
        boolean devOptions = props.game() != null && props.game().devOptions();
        List.of(game2048(devOptions), stairs()).forEach(s -> specs.put(s.id(), s));
        this.sharedGames = sharedGames.stream().collect(Collectors.toMap(SharedGame::gameId, g -> g));
    }

    public Optional<SharedGame> sharedGame(String id) {
        return Optional.ofNullable(sharedGames.get(id));
    }

    private static GameSpec game2048(boolean devOptions) {
        var targets = new ArrayList<Object>(List.of(512, 1024, 2048));
        if (devOptions) targets.add(0, 64);
        return new GameSpec("2048", "2048", 1, 4, 8, Duration.ofMinutes(3), true, true,
                Map.of("target", List.copyOf(targets)), Map.of("target", 2048), List.of(GameMode.VERSUS));
    }

    private static GameSpec stairs() {
        return new GameSpec("stairs", "계단 오르기", 1, 4, 8, null, true, true,
                Map.of("speed", List.of("normal", "fast")), Map.of("speed", "normal"), List.of(GameMode.VERSUS, GameMode.COOP));
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
