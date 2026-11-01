package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;

/** Uses the protected courtyard, with sixteen spawns around its outside lanes. */
final class ArmsRaceArena {
    private final PvpArena geometry;
    private final List<Location> spawns = new ArrayList<>();

    ArmsRaceArena(World world) {
        geometry = new PvpArena(world);
        for (int side : new int[] {-1, 1}) {
            for (int along : new int[] {-19, -10, 0, 10, 19}) {
                add(world, side * 19, along);
                if (Math.abs(along) != 19) add(world, along, side * 19);
            }
        }
    }
    private void add(World world, int x, int z) {
        Location location = new Location(world, x + .5, PvpArena.FLOOR + 1, z + .5);
        location.setDirection(geometry.center().toVector().subtract(location.toVector()));
        spawns.add(location);
    }
    void build() { geometry.build(); }
    void cleanup() { geometry.reset(); }
    Location center() { return geometry.center(); }
    Location spectator() { return geometry.spectator(); }
    boolean contains(Location location) { return geometry.contains(location); }
    List<Location> spawns() { return spawns.stream().map(Location::clone).toList(); }

    /** Maximise distance to the nearest enemy; shuffle only to break equal-distance choices. */
    Location spawn(List<Location> enemies, Random random) {
        List<Location> candidates = new ArrayList<>(spawns);
        Collections.shuffle(candidates, random);
        Location best = candidates.getFirst();
        double bestDistance = -1;
        for (Location candidate : candidates) {
            double nearest = Double.POSITIVE_INFINITY;
            for (Location enemy : enemies) {
                if (!candidate.getWorld().equals(enemy.getWorld())) continue;
                double x = candidate.getX() - enemy.getX(), z = candidate.getZ() - enemy.getZ();
                nearest = Math.min(nearest, x * x + z * z);
            }
            if (nearest > bestDistance) { best = candidate; bestDistance = nearest; }
        }
        return best.clone();
    }
}
