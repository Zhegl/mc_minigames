package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

final class TntRunArena {
    static final int RADIUS = 16;
    static final int[] FLOORS = {86, 80, 74};
    record Tile(int x, int y, int z) { }
    private final World world;
    private final Map<Tile, Long> collapsing = new HashMap<>();
    private int radius = RADIUS;

    TntRunArena(World world) { this.world = world; }
    static boolean inside(int x, int z, int radius) { return radius >= 0 && x * x + z * z <= radius * radius; }
    void build() {
        for (int y : FLOORS) for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            if (inside(x, z, RADIUS)) world.getBlockAt(x, y, z).setType(Material.TNT, false);
        }
        world.setSpawnLocation(0, FLOORS[0] + 1, 0);
    }
    Location spawn(int index, int count) {
        double angle = Math.PI * 2 * index / Math.max(1, count);
        return new Location(world, Math.floor(Math.cos(angle) * 10) + .5, FLOORS[0] + 1,
                Math.floor(Math.sin(angle) * 10) + .5, (float) Math.toDegrees(angle) + 90, 0);
    }
    void step(Location feet, long tick, int delay) {
        int y = (int) Math.floor(feet.getY() - .15);
        boolean floor = false;
        for (int level : FLOORS) if (level == y) floor = true;
        if (!floor) return;
        for (int x = (int) Math.floor(feet.getX() - .28); x <= (int) Math.floor(feet.getX() + .28); x++) {
            for (int z = (int) Math.floor(feet.getZ() - .28); z <= (int) Math.floor(feet.getZ() + .28); z++) {
                Tile tile = new Tile(x, y, z);
                if (world.getBlockAt(x, y, z).getType() != Material.TNT || collapsing.containsKey(tile)) continue;
                collapsing.put(tile, tick + Math.max(2, delay));
                world.getBlockAt(x, y, z).setType(Material.RED_CONCRETE, false);
            }
        }
    }
    void tick(long tick, int nextRadius) {
        var iterator = collapsing.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (tick < entry.getValue()) continue;
            Tile tile = entry.getKey();
            world.getBlockAt(tile.x(), tile.y(), tile.z()).setType(Material.AIR, false);
            iterator.remove();
        }
        if (nextRadius >= radius) return;
        for (int y : FLOORS) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (inside(x, z, radius) && !inside(x, z, nextRadius)) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        radius = nextRadius;
    }
    static int delay(int initial, long elapsed) { return (int) Math.max(3, initial - elapsed / (30 * 20)); }
    static int radius(long elapsed) { return elapsed < 60 * 20 ? RADIUS : (int) Math.max(5, RADIUS - 1 - (elapsed - 60 * 20) / (15 * 20)); }
}
