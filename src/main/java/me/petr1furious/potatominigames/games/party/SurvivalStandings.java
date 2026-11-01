package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A fixed roster: elimination and withdrawal never allow a second life. */
final class SurvivalStandings {
    private final Set<UUID> alive;
    private final Map<UUID, Long> outAt = new LinkedHashMap<>();
    private long lastOut = -1;
    private boolean resultsHeld;

    SurvivalStandings(Collection<UUID> players) { alive = new LinkedHashSet<>(players); }
    boolean alive(UUID id) { return alive.contains(id); }
    List<UUID> survivors() { return List.copyOf(alive); }
    boolean eliminate(UUID id, long tick) {
        if (!alive.remove(id)) return false;
        outAt.put(id, tick);
        lastOut = tick;
        return true;
    }
    boolean decided(boolean test, long tick) {
        return !resultsHeld && (alive.isEmpty() || !test && alive.size() == 1 && lastOut >= 0 && tick - lastOut >= 2);
    }
    void holdResults(boolean hold) { resultsHeld = hold; }
    boolean resultsHeld() { return resultsHeld; }
    long survived(UUID id, long now) { return outAt.getOrDefault(id, now); }
    List<UUID> ranking(Collection<UUID> roster, long now) {
        List<UUID> result = new ArrayList<>(roster);
        result.sort(Comparator.<UUID>comparingLong(id -> survived(id, now)).reversed());
        return List.copyOf(result);
    }
}
