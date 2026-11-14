package me.petr1furious.potatominigames.games.party;

import java.util.*;

/** Private per-player choices, locked together before the ordinary FFA countdown. */
final class PvpKitChoices {
    static final int SECONDS = 30;
    private final Set<UUID> players;
    private final Map<UUID, PvpClass> picks = new HashMap<>();
    private boolean locked;

    PvpKitChoices(Collection<UUID> players) {
        this.players = new LinkedHashSet<>(players);
    }

    boolean choose(UUID id, PvpClass kit) {
        if (locked || kit == null || !players.contains(id)) return false;
        picks.put(id, kit);
        return true;
    }

    void withdraw(UUID id) {
        players.remove(id);
        picks.remove(id);
    }

    PvpClass selected(UUID id) {
        return picks.get(id);
    }

    int picked() {
        return picks.size();
    }

    int size() {
        return players.size();
    }

    boolean ready() {
        return !players.isEmpty() && picks.keySet().containsAll(players);
    }

    Map<UUID, PvpClass> finish(Random random) {
        if (!locked) {
            for (UUID id : players) picks.computeIfAbsent(id, ignored -> PvpClass.random(random));
            locked = true;
        }
        return Map.copyOf(picks);
    }
}
