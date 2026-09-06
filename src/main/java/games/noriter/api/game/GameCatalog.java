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
    private final Map<String, ScoreReplayer> replayers;

    public GameCatalog(NoriterProperties props, List<TurnGame> turnGames, List<ScoreReplayer> replayers) {
        boolean devOptions = props.game() != null && props.game().devOptions();
        List.of(game2048(devOptions), stairs(), yut(), word()).forEach(s -> specs.put(s.id(), s));
        this.turnGames = turnGames.stream().collect(Collectors.toMap(TurnGame::gameId, g -> g));
        this.replayers = replayers.stream().collect(Collectors.toMap(ScoreReplayer::gameId, r -> r));
    }

    public Optional<TurnGame> turnGame(String id) {
        return Optional.ofNullable(turnGames.get(id));
    }

    public Optional<ScoreReplayer> replayer(String id) {
        return Optional.ofNullable(replayers.get(id));
    }

    private static GameSpec game2048(boolean devOptions) {
        var targets = new ArrayList<Object>(List.of(512, 1024, 2048));
        if (devOptions) targets.add(0, 64);
        // 한 수에 1024 쌍 넷을 합쳐도 8192, 초당 15수를 눌러도 수백 점. 2048 타일까지 판 전체 점수는 4만 안팎
        var limits = new GameSpec.ScoreLimits(1_000, 8_192, 65_536);
        return new GameSpec("2048", "2048", 1, 4, 8, Duration.ofMinutes(3), true, true,
                Map.of("target", List.copyOf(targets)), Map.of("target", 2048), false, false, limits);
    }

    private static GameSpec stairs() {
        // 부스터(한 번에 4칸)를 섞어 초당 15번 눌러도 40칸 아래. 감소 속도가 칸마다 5% 커져 200칸 넘기기도 어렵다
        var limits = new GameSpec.ScoreLimits(40, 8, 1_000);
        return new GameSpec("stairs", "계단 오르기", 1, 4, 8, null, true, true, Map.of(), Map.of(), false, false, limits);
    }

    private static GameSpec yut() {
        return new GameSpec("yut", "윷놀이", 2, 4, 4, null, true, true,
                Map.of("pieces", List.of(2, 3, 4), "cards", List.of(true, false)), Map.of("pieces", 3, "cards", true), true, true);
    }

    private static GameSpec word() {
        return new GameSpec("word", "글딱지", 1, 1, 1, null, false, true, Map.of(), Map.of(), false, false);
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
