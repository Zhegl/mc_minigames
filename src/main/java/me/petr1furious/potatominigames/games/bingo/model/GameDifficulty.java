package me.petr1furious.potatominigames.games.bingo.model;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public record GameDifficulty(String key, int order, int targetScore, int maxDeviation, Map<GameMode, Integer> durations)
        implements Comparable<GameDifficulty> {
    public GameDifficulty {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Difficulty key cannot be blank.");
        }
        key = key.toLowerCase(Locale.ROOT);
        EnumMap<GameMode, Integer> normalizedDurations = new EnumMap<>(GameMode.class);
        if (durations != null) {
            normalizedDurations.putAll(durations);
        }
        for (GameMode mode : GameMode.values()) {
            Integer seconds = normalizedDurations.get(mode);
            if (seconds == null || seconds <= 0) {
                throw new IllegalArgumentException(
                        "Difficulty '" + key + "' is missing duration for mode " + mode.key() + ".");
            }
        }
        durations = Map.copyOf(normalizedDurations);
    }

    @Override
    public int compareTo(GameDifficulty other) {
        return Integer.compare(order, other.order);
    }

    public int durationFor(GameMode mode) {
        return durations.get(mode);
    }
}
