package games.noriter.api.game;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record GameSpec(
        String id,
        String name,
        int minPlayers,
        int defaultMaxPlayers,
        int maxPlayersLimit,
        Duration matchDuration,
        boolean seeded,
        boolean higherIsBetter,
        Map<String, List<Object>> optionChoices,
        Map<String, Object> defaultOptions,
        List<GameMode> modes) {

    public GameSpec {
        if (minPlayers < 1 || defaultMaxPlayers < minPlayers || maxPlayersLimit < defaultMaxPlayers) {
            throw new IllegalArgumentException("invalid player bounds for " + id);
        }
        var choices = Map.copyOf(optionChoices);
        var defaults = Map.copyOf(defaultOptions);
        defaults.forEach((k, v) -> {
            if (!allowed(choices, k, v)) throw new IllegalArgumentException("default option not in choices: " + k + "=" + v);
        });
        optionChoices = choices;
        defaultOptions = defaults;
        modes = List.copyOf(modes);
        if (!modes.contains(GameMode.VERSUS)) throw new IllegalArgumentException("VERSUS mode is required: " + id);
    }

    public boolean supports(GameMode mode) {
        return modes.contains(mode);
    }

    public boolean isAllowedOption(String key, Object value) {
        return allowed(optionChoices, key, value);
    }

    private static boolean allowed(Map<String, List<Object>> choices, String key, Object value) {
        var list = choices.get(key);
        return list != null && list.stream().anyMatch(c -> String.valueOf(c).equals(String.valueOf(value)));
    }
}
