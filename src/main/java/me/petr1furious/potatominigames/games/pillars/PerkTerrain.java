package me.petr1furious.potatominigames.games.pillars;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

/** Writes only into air; temporary blocks restore only if nobody has replaced them. */
final class PerkTerrain {
    record Pos(int x, int y, int z) { static Pos of(Block b) { return new Pos(b.getX(), b.getY(), b.getZ()); } }
    private record Lease(Block block, BlockData original, BlockData placed, long until, boolean locked) { }
    private final World world;
    private final Map<Pos, Lease> leases = new HashMap<>();
    PerkTerrain(World world) { this.world = world; }
    boolean locked(Block b, long tick) { Lease l = leases.get(Pos.of(b)); return l != null && l.locked() && tick < l.until(); }
    void changed(Block b) { leases.remove(Pos.of(b)); }
    private boolean air(Block b) { return b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR; }
    boolean put(Block b, Material material, long until, boolean locked) {
        if (b.getY() < world.getMinHeight() || b.getY() >= world.getMaxHeight() || !world.getWorldBorder().isInside(b.getLocation())) return false;
        Pos pos = Pos.of(b);
        Lease previous = leases.get(pos);
        if (previous != null && b.getBlockData().equals(previous.placed()) && b.getType() == material) {
            leases.put(pos, new Lease(b, previous.original(), previous.placed(), Math.max(until, previous.until()), locked || previous.locked()));
            return true;
        }
        if (!air(b) || leases.size() >= 8192) return false;
        BlockData original = b.getBlockData().clone();
        b.setType(material, false);
        leases.put(pos, new Lease(b, original, b.getBlockData().clone(), until, locked));
        return true;
    }
    boolean platform(Location at, long until) {
        int y = at.getBlockY() - 2;
        boolean placed = false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            placed |= put(world.getBlockAt(at.getBlockX() + x, y, at.getBlockZ() + z), Material.SLIME_BLOCK, until, false);
        return placed;
    }
    void cage(Location at, long until) {
        int floor = at.getBlockY() - 1;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 3; y++) {
            if (x == 0 && z == 0 && y > 0 && y < 3) continue;
            put(world.getBlockAt(at.getBlockX() + x, floor + y, at.getBlockZ() + z), Material.ICE, until, true);
        }
    }
    static Map<Pos, Material> housePlan() {
        Map<Pos, Material> plan = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            plan.put(new Pos(x, -1, z), Material.OAK_PLANKS);
            plan.put(new Pos(x, 2, z), Material.OAK_SLAB);
            if (x == 0 && z == 0) continue;
            for (int y = 0; y <= 1; y++) plan.put(new Pos(x, y, z), Math.abs(x) == 1 && Math.abs(z) == 1 ? Material.OAK_LOG : Material.OAK_PLANKS);
        }
        plan.put(new Pos(0, 0, -1), Material.OAK_DOOR);
        plan.put(new Pos(0, 1, -1), Material.OAK_DOOR);
        plan.put(new Pos(1, 1, 0), Material.GLASS_PANE);
        plan.put(new Pos(-1, 1, 0), Material.GLASS_PANE);
        plan.put(new Pos(0, 1, 0), Material.WALL_TORCH);
        return Map.copyOf(plan);
    }
    boolean house(Location at) {
        // Preflight the whole structure before spending the item or changing any blocks.
        var plan = housePlan();
        for (var entry : plan.entrySet()) {
            Pos pos = entry.getKey(); Block b = world.getBlockAt(at.getBlockX() + pos.x(), at.getBlockY() + pos.y(), at.getBlockZ() + pos.z());
            if (b.getY() < world.getMinHeight() || b.getY() >= world.getMaxHeight() || !world.getWorldBorder().isInside(b.getLocation())) return false;
            if (!air(b) && pos.y() != -1) return false;
        }
        for (var entry : plan.entrySet()) {
            Pos pos = entry.getKey(); Block b = world.getBlockAt(at.getBlockX() + pos.x(), at.getBlockY() + pos.y(), at.getBlockZ() + pos.z());
            if (!air(b)) continue; // Preserve the existing supporting pillar/floor.
            BlockData data = entry.getValue().createBlockData();
            if (data instanceof Door door) {
                door.setFacing(BlockFace.NORTH);
                door.setHalf(pos.y() == 0 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            }
            if (data instanceof org.bukkit.block.data.Directional direction && entry.getValue() == Material.WALL_TORCH)
                direction.setFacing(BlockFace.NORTH);
            b.setBlockData(data, false);
        }
        return true;
    }
    void tick(long tick) {
        var it = leases.values().iterator();
        while (it.hasNext()) { Lease lease = it.next(); if (tick >= lease.until()) { restore(lease); it.remove(); } }
    }
    private void restore(Lease l) { if (l.block().getBlockData().equals(l.placed())) l.block().setBlockData(l.original(), false); }
    void close() { for (Lease l : leases.values()) restore(l); leases.clear(); }
}
