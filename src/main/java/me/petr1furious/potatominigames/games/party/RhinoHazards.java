package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Difficulty and swept collision rules shared by the live hazards and server-free checks. */
final class RhinoHazards {
    private RhinoHazards() { }
    static int stage(long elapsed) { return (int) Math.min(10, elapsed / (45 * 20)); }
    static int radius(long elapsed) { return elapsed < 45 * 20 ? 18 : (int) Math.max(5, 17 - (elapsed - 45 * 20) / (15 * 20)); }
    static double speed(int stage) { return Math.min(1.2, .65 + stage * .10); }
    static int cooldown(int stage) { return Math.max(35, 130 - stage * 15); }
    static int windup(int stage) { return Math.max(10, 28 - stage * 3); }
    static int mineInterval(int initialSeconds, int stage) { return Math.max(20, initialSeconds * 20 - stage * 10); }
    static int mineBatch(int stage) { return Math.min(3, 1 + stage / 2); }
    static int targetIndex(List<UUID> players, List<UUID> assigned, Random random) {
        List<Integer> candidates = new ArrayList<>();
        int fewest = Integer.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            int count = Collections.frequency(assigned, players.get(i));
            if (count < fewest) { fewest = count; candidates.clear(); }
            if (count == fewest) candidates.add(i);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
    static double segmentDistanceSquared(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, length = dx * dx + dz * dz;
        double t = length < 1e-9 ? 0 : Math.clamp(((px - ax) * dx + (pz - az) * dz) / length, 0, 1);
        double x = px - (ax + t * dx), z = pz - (az + t * dz);
        return x * x + z * z;
    }
}
