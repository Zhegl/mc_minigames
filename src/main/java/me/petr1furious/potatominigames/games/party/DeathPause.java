package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Minimum result-reading time, independent of clicking the vanilla Respawn button. */
final class DeathPause {
    static final int TICKS = 60;
    private final Map<UUID, Long> until = new HashMap<>();

    void begin(UUID player, long tick) { until.putIfAbsent(player, tick + TICKS); }
    boolean pending(UUID player, long tick) { return remaining(player, tick) > 0; }
    long remaining(UUID player, long tick) { return Math.max(0, until.getOrDefault(player, tick) - tick); }
    long remaining(long tick) { return until.values().stream().mapToLong(end -> Math.max(0, end - tick)).max().orElse(0); }
    void forget(UUID player) { until.remove(player); }
    void clear() { until.clear(); }
}
