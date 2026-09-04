package games.noriter.api.game;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GameCatalog {

    private final Map<String, GameSpec> specs = new LinkedHashMap<>();

    public GameCatalog() {
        this(List.of(
                new GameSpec("2048", "2048", 1, 4, 8, Duration.ofMinutes(3), true, true,
                        Map.of("target", List.of(512, 1024, 2048)),
                        Map.of("target", 2048))));
    }

    GameCatalog(Collection<GameSpec> initial) {
        initial.forEach(s -> specs.put(s.id(), s));
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
