package me.petr1furious.potatominigames.games.bingo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.games.bingo.model.GameDifficulty;
import me.petr1furious.potatominigames.games.bingo.model.GameMode;

public final class BingoConfig {
    public static final String FILE = "games/bingo.yml";

    private final ConfigRegistry configs;

    private final PropertyBag bag = new PropertyBag("bingo");
    public final Property<String> mode = bag.define("mode", PropertyCodec.STRING, "default")
            .oneOf(GameMode::keys);
    public final Property<String> difficulty = bag.define("difficulty", PropertyCodec.STRING, "medium")
            .oneOf(this::difficultyKeys);
    public final Property<Integer> punishmentSeconds = bag.define("punishmentSeconds", PropertyCodec.INT, 10);
    public final Property<Integer> afterGameSeconds = bag.define("afterGameSeconds", PropertyCodec.INT, 10);
    /** Seconds caged above the arena before the drop. */
    public final Property<Integer> countdownSeconds = bag.define("countdownSeconds", PropertyCodec.INT, 10);

    private final Map<String, GameDifficulty> difficultiesByKey = new LinkedHashMap<>();

    public BingoConfig(ConfigRegistry configs) {
        this.configs = configs;
        configs.register(bag, FILE, "");
        loadDifficultyLevels();
        // A list of maps; edited in the file.
        configs.hide(FILE, "difficulty-levels");
        configs.onReload(FILE, this::loadDifficultyLevels);
    }

    public void reloadDifficulties() {
        loadDifficultyLevels();
    }

    public GameMode gameMode() {
        try {
            return GameMode.fromKey(mode.get());
        } catch (IllegalArgumentException exception) {
            return GameMode.DEFAULT;
        }
    }

    public GameDifficulty gameDifficulty() {
        GameDifficulty found = difficultiesByKey.get(difficulty.get().toLowerCase(Locale.ROOT));
        return found != null ? found : difficulties().getFirst();
    }

    public List<GameDifficulty> difficulties() {
        List<GameDifficulty> list = new ArrayList<>(difficultiesByKey.values());
        list.sort(Comparator.naturalOrder());
        return List.copyOf(list);
    }

    public List<String> difficultyKeys() {
        return difficulties().stream().map(GameDifficulty::key).toList();
    }

    public GameDifficulty requireDifficulty(String key) throws CommandFailure {
        GameDifficulty found = difficultiesByKey.get(key.toLowerCase(Locale.ROOT));
        if (found == null) {
            throw new CommandFailure("Unknown difficulty: " + key + " (options: "
                    + String.join(", ", difficultyKeys()) + ")");
        }
        return found;
    }

    private void loadDifficultyLevels() {
        difficultiesByKey.clear();
        List<Map<?, ?>> levels = configs.file(FILE).getMapList("difficulty-levels");
        if (levels.isEmpty()) {
            throw new IllegalStateException("games/bingo.yml: difficulty-levels must be a non-empty list.");
        }
        int index = 0;
        for (Map<?, ?> level : levels) {
            String key = String.valueOf(level.get("key")).toLowerCase(Locale.ROOT);
            if (key.isBlank() || key.equals("null")) {
                throw new IllegalStateException("games/bingo.yml: difficulty-levels entry is missing key.");
            }
            int targetScore = intValue(level.get("target-score"), 50);
            int maxDeviation = Math.max(0, intValue(level.get("max-deviation"), 20));
            Map<GameMode, Integer> durations = readDurations(level, key);
            difficultiesByKey.putIfAbsent(key,
                    new GameDifficulty(key, index++, targetScore, maxDeviation, durations));
        }
    }

    private static Map<GameMode, Integer> readDurations(Map<?, ?> level, String key) {
        Object rawDurations = level.get("durations");
        if (!(rawDurations instanceof Map<?, ?> durationsMap)) {
            throw new IllegalStateException("games/bingo.yml: difficulty '" + key + "' is missing durations.");
        }
        Map<GameMode, Integer> durations = new EnumMap<>(GameMode.class);
        for (GameMode gameMode : GameMode.values()) {
            Object rawValue = durationsMap.get(gameMode.key());
            if (rawValue == null && gameMode == GameMode.LOCKOUT) {
                // Defaults to the default mode's duration (see GameDifficulty).
                continue;
            }
            if (rawValue == null) {
                throw new IllegalStateException(
                        "games/bingo.yml: difficulty '" + key + "' is missing durations." + gameMode.key());
            }
            durations.put(gameMode, Math.max(1, intValue(rawValue, 1)));
        }
        return durations;
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
