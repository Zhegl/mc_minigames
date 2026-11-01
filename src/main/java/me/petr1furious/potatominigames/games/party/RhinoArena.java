package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

final class RhinoArena {
    static final int FLOOR = 80, RADIUS = 18;
    private final World world;
    private int radius = -1;
    RhinoArena(World world) { this.world = world; }
    static boolean hole(int x, int z) { return x * x + z * z > 12 && x * x + z * z < 14 * 14 && Math.floorMod(x * 31 + z * 17, 37) == 0; }
    static boolean solid(int x, int z, int radius) { return TntRunArena.inside(x, z, radius) && !hole(x, z); }
    void resize(int next) {
        if (radius == next) return;
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            world.getBlockAt(x, FLOOR, z).setType(!solid(x, z, next) ? Material.AIR
                    : !TntRunArena.inside(x, z, next - 1) ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE, false);
        }
        radius = next;
    }
    Location safe(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int ring = 0; ring <= RADIUS * 2; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                if (solid(bx + dx, bz + dz, radius)) return new Location(world, bx + dx + .5, FLOOR + 1, bz + dz + .5);
            }
        }
        return new Location(world, .5, FLOOR + 1, .5);
    }
    Location spawn(int index, int count) {
        double angle = index * Math.PI * 2 / Math.max(1, count);
        return safe(Math.cos(angle) * 12, Math.sin(angle) * 12);
    }
}
