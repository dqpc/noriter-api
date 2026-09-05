package games.noriter.api.game;

import games.noriter.api.config.NoriterProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GameCatalog {

    private final Map<String, GameSpec> specs = new LinkedHashMap<>();

    public GameCatalog(NoriterProperties props) {
        boolean devOptions = props.game() != null && props.game().devOptions();
        List.of(game2048(devOptions), stairs()).forEach(s -> specs.put(s.id(), s));
    }

    private static GameSpec game2048(boolean devOptions) {
        var targets = new ArrayList<Object>(List.of(512, 1024, 2048));
        if (devOptions) targets.add(0, 64);
        return new GameSpec("2048", "2048", 1, 4, 8, Duration.ofMinutes(3), true, true,
                Map.of("target", List.copyOf(targets)), Map.of("target", 2048));
    }

    private static GameSpec stairs() {
        return new GameSpec("stairs", "계단 오르기", 1, 4, 8, null, true, true, Map.of(), Map.of());
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
