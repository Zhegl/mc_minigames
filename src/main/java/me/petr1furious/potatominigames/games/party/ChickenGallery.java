package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/** Repeatable target paths and a score table shared by all the lanes. */
final class ChickenGallery {
    private static final int[] WAVE_TICKS = {100, 100, 90, 90, 80, 80, 80, 70, 70, 60};
    static final List<Wave> WAVES =
            IntStream.range(0, WAVE_TICKS.length).mapToObj(ChickenGallery::pattern).toList();
    static final int DURATION_TICKS = WAVES.stream().mapToInt(Wave::ticks).sum();

    private final Map<UUID, Integer> scores = new LinkedHashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();

    ChickenGallery(Collection<UUID> players) {
        players.forEach(id -> scores.put(id, 0));
    }

    static int wave(long elapsed) {
        int index = 0;
        for (Wave wave : WAVES) {
            if (elapsed < wave.ticks()) return index;
            elapsed -= wave.ticks();
            index++;
        }
        return index;
    }

    private static Wave pattern(int wave) {
        if (wave == 0)
            return new Wave(
                    WAVE_TICKS[0],
                    1.9,
                    List.of(
                            new Bird(0, 7, 0, 0, 0, 100, 0),
                            new Bird(-3, 9, 0, 0, 0, 100, 0),
                            new Bird(3, 9, 0, 0, 0, 100, 0)));
        double phase = wave * Math.PI / 2;
        int period = 92 - wave * 5;
        return new Wave(
                WAVE_TICKS[wave],
                Math.max(.9, 1.63 - wave * .085),
                List.of(
                        new Bird(0, 8 + wave * .55, 1.1 + wave * .12, 1.15, .3, period, phase),
                        new Bird(
                                -4.2,
                                11.5 + wave * .55,
                                1.2 + wave * .07,
                                1.1,
                                .55,
                                period + 9,
                                phase + Math.PI),
                        new Bird(
                                4.2,
                                15 + wave * .4,
                                1.35 + wave * .08,
                                1.45,
                                .45,
                                period + 4,
                                phase)));
    }

    record Wave(int ticks, double scale, List<Bird> birds) {}

    record Bird(
            double center,
            double depth,
            double drift,
            double depthDrift,
            double lift,
            int period,
            double phase) {
        double x(long age) {
            return center + drift * Math.sin(angle(age));
        }

        double z(long age) {
            return depth + depthDrift * Math.cos(angle(age));
        }

        double y(long age) {
            return lift * Math.sin(angle(age) * 2);
        }

        private double angle(long age) {
            return phase + age * (2 * Math.PI / period);
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
