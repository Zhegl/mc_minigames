package me.petr1furious.potatominigames.games.party;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;

/** Symmetric 49x49 courtyard. Only tracked player blocks are mutable. */
final class PvpArena extends DuelArena {
    static final int FLOOR = 80;
    static final int HALF = 24;
    static final int MAX_BUILD_Y = FLOOR + 5;
    record Position(int x, int y, int z) {
        static Position of(Block b) { return new Position(b.getX(), b.getY(), b.getZ()); }
    }
    private final World world;
    private final Set<Position> placed = new HashSet<>();
    PvpArena(World world) { this.world = world; }
    void build() {
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                set(x, FLOOR - 1, z, Material.BEDROCK);
                set(x, FLOOR, z, Math.abs(x) <= 2 || Math.abs(z) <= 2
                        ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                if (Math.abs(x) == 25 || Math.abs(z) == 25) {
                    for (int y = FLOOR + 1; y <= FLOOR + 9; y++) {
                        set(x, y, z, y <= FLOOR + 3 ? Material.DEEPSLATE_BRICKS : Material.BARRIER);
                    }
                }
                set(x, FLOOR + 10, z, Material.BARRIER);
            }
        }
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                // Short cover with gaps around both ends; no protected high camping spots.
                for (int dx = -2; dx <= 2; dx++) {
                    for (int y = 1; y <= 2; y++) set(sx * 10 + dx, FLOOR + y, sz * 8, Material.MOSSY_STONE_BRICKS);
                }
                for (int dz = -1; dz <= 1; dz++) set(sx * 18, FLOOR + 1, sz * 15 + dz, Material.STONE_BRICKS);
                set(sx * 4, FLOOR + 1, sz * 5, Material.CHISELED_STONE_BRICKS);
                set(sx * 4, FLOOR + 2, sz * 5, Material.CHISELED_STONE_BRICKS);
            }
        }
        world.setSpawnLocation(0, FLOOR + 1, 0);
    }
    private void set(int x, int y, int z, Material material) { world.getBlockAt(x, y, z).setType(material, false); }
    Location center() { return new Location(world, .5, FLOOR + 1, .5); }
    Location spawn(boolean east) { return new Location(world, east ? 19.5 : -18.5, FLOOR + 1, .5, east ? 90 : -90, 0); }
    Location spectator() { return new Location(world, .5, FLOOR + 8, .5, 0, 60); }
    boolean canPlace(Block block) {
        return block.getWorld().equals(world) && Math.abs(block.getX()) <= HALF && Math.abs(block.getZ()) <= HALF
                && block.getY() > FLOOR && block.getY() <= MAX_BUILD_Y;
    }
    void track(Block block) { placed.add(Position.of(block)); }
    boolean isPlaced(Block block) { return placed.contains(Position.of(block)); }
    void broken(Block block) { placed.remove(Position.of(block)); }
    void reset() {
        for (Position p : placed) set(p.x(), p.y(), p.z(), Material.AIR);
        placed.clear();
        for (var entity : world.getEntities()) {
            if (entity instanceof Projectile || entity instanceof Item) entity.remove();
        }
    }
    boolean contains(Location loc) {
        return loc.getWorld().equals(world) && loc.getX() >= -24.7 && loc.getX() <= 25.7
                && loc.getZ() >= -24.7 && loc.getZ() <= 25.7
                && loc.getY() >= FLOOR && loc.getY() < FLOOR + 9;
    }
}
