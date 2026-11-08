package me.petr1furious.potatominigames.games.party;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

final class RhinoArena {
    static final int FLOOR = 80, RADIUS = 18;
    private final World world;
    private int radius = -1;
    private int stage = -1;
    RhinoArena(World world) { this.world = world; }
    // Each stage adds gaps to the existing pattern, leaving the central refuge intact.
    static boolean hole(int x, int z, int stage) {
        return x * x + z * z > 12 && x * x + z * z < 14 * 14
                && Math.floorMod(x * 31 + z * 17, 37) <= Math.clamp(stage, 0, 10);
    }
    static boolean solid(int x, int z, int radius, int stage) { return TntRunArena.inside(x, z, radius) && !hole(x, z, stage); }
    void resize(int next, int nextStage) {
        if (radius == next && stage == nextStage) return;
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            world.getBlockAt(x, FLOOR, z).setType(!solid(x, z, next, nextStage) ? Material.AIR
                    : !TntRunArena.inside(x, z, next - 1) ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE, false);
        }
        radius = next;
        stage = nextStage;
    }
    Location safe(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int ring = 0; ring <= RADIUS * 2; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                if (solid(bx + dx, bz + dz, radius, stage)) return new Location(world, bx + dx + .5, FLOOR + 1, bz + dz + .5);
            }
        }
        return new Location(world, .5, FLOOR + 1, .5);
    }
    Location spawn(int index, int count) {
        double angle = index * Math.PI * 2 / Math.max(1, count);
        return safe(Math.cos(angle) * 12, Math.sin(angle) * 12);
    }
    // Separate resting rhinos on solid floor, including when the arena is small.
    Location spread(Location preferred, List<Location> occupied) {
        double inner = Math.max(1, radius - 2);
        boolean safe = preferred.getX() * preferred.getX() + preferred.getZ() * preferred.getZ() <= inner * inner
                && solid(preferred.getBlockX(), preferred.getBlockZ(), radius, stage);
        double originalGap = clearance(preferred, occupied);
        if (safe && originalGap >= 3 * 3) return preferred;
        Location best = null;
        double bestGap = -1, bestDistance = Double.POSITIVE_INFINITY;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if ((x + .5) * (x + .5) + (z + .5) * (z + .5) > inner * inner
                    || !solid(x, z, radius, stage)) continue;
            Location candidate = new Location(world, x + .5, FLOOR + 1, z + .5);
            double gap = Math.min(3.5 * 3.5, clearance(candidate, occupied));
            double distance = candidate.distanceSquared(preferred);
            if (gap > bestGap || (gap == bestGap && distance < bestDistance)) {
                best = candidate; bestGap = gap; bestDistance = distance;
            }
        }
        // Avoid shuffling endlessly if the remaining floor cannot offer more room.
        if (safe && bestGap <= originalGap + .25) return preferred;
        return best == null ? this.safe(0, 0) : best;
    }
    private static double clearance(Location location, List<Location> occupied) {
        double gap = Double.POSITIVE_INFINITY;
        for (Location other : occupied) {
            double dx = location.getX() - other.getX(), dz = location.getZ() - other.getZ();
            gap = Math.min(gap, dx * dx + dz * dz);
        }
        return gap;
    }
}
