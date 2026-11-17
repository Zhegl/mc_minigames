package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Repeatable target paths and a score table shared by all the lanes. */
final class ChickenGallery {
    static final int TICKS_PER_WAVE = 100;
    static final int WAVES = 8;

    private final Map<UUID, Integer> scores = new LinkedHashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();

    ChickenGallery(Collection<UUID> players) {
        players.forEach(id -> scores.put(id, 0));
    }

    static int wave(long elapsed) {
        return (int) (elapsed / TICKS_PER_WAVE);
    }

    static List<Bird> birds(int wave) {
        if (wave == 0)
            return List.of(new Bird(0, 7, 0, 0), new Bird(-3, 9, 0, 0), new Bird(3, 9, 0, 0));
        double phase = wave * Math.PI / 2;
        return List.of(
                new Bird(0, 8, 1.4, phase),
                new Bird(-4, 12 + wave % 3, 1.25, phase + Math.PI),
                new Bird(4, 15 - wave % 3, 1.5, phase));
    }

    record Bird(double center, double z, double drift, double phase) {
        double x(long age) {
            return center + drift * Math.sin(phase + age * (2 * Math.PI / TICKS_PER_WAVE));
        }
    }

    void addBird(UUID bird, UUID owner) {
        if (scores.containsKey(owner)) owners.put(bird, owner);
    }

    void removeBird(UUID bird) {
        owners.remove(bird);
    }

    boolean hit(UUID shooter, UUID bird) {
        if (!owners.remove(bird, shooter)) return false;
        scores.computeIfPresent(shooter, (id, old) -> old + 1);
        return true;
    }

    int score(UUID player) {
        return scores.getOrDefault(player, 0);
    }

    PartyRoundResult result(Set<UUID> eligible) {
        return PartyRoundResult.ordered(
                scores.keySet().stream().filter(eligible::contains).toList(),
                Comparator.comparingInt(this::score).reversed());
    }
}
