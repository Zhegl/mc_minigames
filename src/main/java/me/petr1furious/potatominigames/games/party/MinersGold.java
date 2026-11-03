package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Two finite +2-heart steps. Further gold never becomes repeatable healing. */
final class MinersGold {
    static final int PER_LEVEL = 4, MAX_ORES = 8;
    private final Map<UUID, Integer> gold = new HashMap<>();
    int count(UUID player) { return gold.getOrDefault(player, 0); }
    int level(UUID player) { return count(player) / PER_LEVEL; }
    boolean collect(UUID player) {
        int current = count(player);
        if (current >= MAX_ORES) return false;
        gold.put(player, current + 1);
        return true;
    }
    void clear() { gold.clear(); }
}
