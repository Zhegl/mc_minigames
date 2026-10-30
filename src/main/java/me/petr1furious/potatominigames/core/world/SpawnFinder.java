package me.petr1furious.potatominigames.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.World;

public final class SpawnFinder {
    private final Random random = new Random();

    /** Uniform over the disc, never in water or lava; falls back to the center column, then null. */
    public Location findSpawnAround(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }

        int attempts = Math.max(32, radius * 4);
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * distance);
            Location candidate = Locations.getFirstSolidBlock(new Location(world, x, center.getY(), z));
            // Water is passable, so a column in the sea ends on its floor.
            if (candidate != null && !candidate.getBlock().isLiquid()) {
                return candidate.add(0.5, 0, 0.5);
            }
        }

        Location fallback = Locations.getFirstSolidBlock(center);
        return fallback == null ? null : fallback.add(0.5, 0, 0.5);
    }

    /** Keeps {@code minSeparation} between spawns where possible. */
    public List<Location> spreadAround(Location center, int radius, int count, double minSeparation) {
        List<Location> result = new ArrayList<>();
        int attempts = Math.max(64, count * 24);
        for (int i = 0; i < attempts && result.size() < count; i++) {
            Location candidate = findSpawnAround(center, radius);
            if (candidate == null) {
                continue;
            }
            boolean tooClose = false;
            for (Location existing : result) {
                if (existing.distanceSquared(candidate) < minSeparation * minSeparation) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                result.add(candidate);
            }
        }
        while (result.size() < count) {
            Location fallback = findSpawnAround(center, radius);
            if (fallback == null) {
                break;
            }
            result.add(fallback);
        }
        return result;
    }

    /** Random surface location within ±range of the origin; null after {@code attempts} misses. */
    public Location randomSurfaceLocation(World world, int range, Predicate<Location> filter, int attempts) {
        for (int i = 0; i < attempts; i++) {
            int x = random.nextInt(range * 2 + 1) - range;
            int z = random.nextInt(range * 2 + 1) - range;
            Location candidate = new Location(world, x, world.getMaxHeight(), z);
            if (filter.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
