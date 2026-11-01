package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** An open, circular 19-block platform over the void. No walls or invisible floor. */
final class SumoArena extends DuelArena {
    static final int FLOOR = 80;
    static final int RADIUS = 9;
    private final World world;
    private int radius = -1;

    SumoArena(World world) { this.world = world; }

    @Override void build() {
        reset();
        world.setSpawnLocation(0, FLOOR + 1, 0);
    }
    static boolean onPlatform(int x, int z, int radius) {
        return radius >= 0 && x * x + z * z <= radius * radius;
    }
    static int radiusAt(long elapsed, int roundSeconds) {
        return elapsed < roundSeconds ? RADIUS
                : (int) Math.max(-1, RADIUS - 1 - (elapsed - roundSeconds) / 3);
    }
    @Override void tick(long elapsed, int roundSeconds) { resize(radiusAt(elapsed, roundSeconds)); }

    private void resize(int next) {
        if (next == radius) return;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (!onPlatform(x, z, RADIUS)) continue;
                Material material = !onPlatform(x, z, next) ? Material.AIR
                        : x == 0 && z == 0 ? Material.GOLD_BLOCK
                        : !onPlatform(x, z, next - 1) ? Material.POLISHED_BLACKSTONE
                        : Material.SMOOTH_QUARTZ;
                world.getBlockAt(x, FLOOR, z).setType(material, false);
            }
        }
        radius = next;
    }
    @Override void reset() { resize(RADIUS); }
    @Override Location center() { return new Location(world, .5, FLOOR + 1, .5); }
    @Override Location spawn(boolean east) {
        return new Location(world, east ? 4.5 : -3.5, FLOOR + 1, .5, east ? 90 : -90, 0);
    }
    @Override Location spectator() { return new Location(world, .5, FLOOR + 8, .5, 0, 60); }
    @Override boolean contains(Location location) {
        return location.getWorld().equals(world) && !isOut(location);
    }
    @Override boolean isOut(Location location) {
        return !location.getWorld().equals(world) || location.getY() < FLOOR - .5;
    }
}
