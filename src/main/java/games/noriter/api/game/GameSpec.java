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
        boolean turnBased,
        boolean uniqueCharacters,
        ScoreLimits scoreLimits) {

    /**
     * 클라이언트가 보고하는 점수의 개연성 한도. 규칙이 브라우저에 있는 게임만 갖고, 서버 판정 게임은 null.
     * maxPerSecond: 시작 후 경과 시간 대비 상한, maxJump: 메시지 한 번에 오를 수 있는 최소 허용폭(시간 허용치와 큰 쪽), maxScore: 절대 상한
     */
    public record ScoreLimits(long maxPerSecond, long maxJump, long maxScore) {
        public ScoreLimits {
            if (maxPerSecond <= 0 || maxJump <= 0 || maxScore <= 0) throw new IllegalArgumentException("limits must be positive");
        }
    }

    public GameSpec(String id, String name, int minPlayers, int defaultMaxPlayers, int maxPlayersLimit, Duration matchDuration,
                    boolean seeded, boolean higherIsBetter, Map<String, List<Object>> optionChoices, Map<String, Object> defaultOptions,
                    boolean turnBased, boolean uniqueCharacters) {
        this(id, name, minPlayers, defaultMaxPlayers, maxPlayersLimit, matchDuration, seeded, higherIsBetter, optionChoices, defaultOptions,
                turnBased, uniqueCharacters, null);
    }

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
    }

    public boolean isAllowedOption(String key, Object value) {
        return allowed(optionChoices, key, value);
    }

    private static boolean allowed(Map<String, List<Object>> choices, String key, Object value) {
        var list = choices.get(key);
        return list != null && list.stream().anyMatch(c -> String.valueOf(c).equals(String.valueOf(value)));
    }
}
