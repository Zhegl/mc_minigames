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
import org.bukkit.inventory.InventoryHolder;

/** Temporary structures preserve existing blocks; transmutation preflights the complete component. */
final class PerkTerrain {
    record Pos(int x, int y, int z) { static Pos of(Block b) { return new Pos(b.getX(), b.getY(), b.getZ()); } }
    private record Lease(Block block, BlockData original, BlockData placed, long until, boolean locked) { }
    private final World world;
    private final Map<Pos, Lease> leases = new HashMap<>();
    PerkTerrain(World world) { this.world = world; }
    boolean locked(Block b, long tick) { Lease l = leases.get(Pos.of(b)); return l != null && l.locked() && tick < l.until(); }
    void changed(Block b) { leases.remove(Pos.of(b)); }
    static boolean air(Block b) { return b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR; }
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
    int transmute(Block start, long tick, java.util.random.RandomGenerator random) {
        boolean[] unloaded = {false};
        var cells = PerkShapes.connected(new PerkShapes.Cell(start.getX(), start.getY(), start.getZ()), c -> {
            if (c.y() < world.getMinHeight() || c.y() >= world.getMaxHeight()) return false;
            if (!world.isChunkLoaded(c.x() >> 4, c.z() >> 4)) { unloaded[0] = true; return false; }
            Block b = world.getBlockAt(c.x(), c.y(), c.z());
            return !air(b) && !b.isLiquid() && !locked(b, tick) && world.getWorldBorder().isInside(b.getLocation());
        }, PerkRules.CONNECTED_LIMIT);
        if (cells == null || unloaded[0]) return -1;
        Material[] palette = {Material.STONE, Material.COBBLESTONE, Material.OAK_PLANKS, Material.GLASS,
                Material.SLIME_BLOCK, Material.HONEY_BLOCK, Material.MOSS_BLOCK, Material.OBSIDIAN, Material.DIAMOND_BLOCK};
        Material original = start.getType();
        boolean uniform = cells.stream().allMatch(c -> world.getBlockAt(c.x(), c.y(), c.z()).getType() == original);
        var choices = java.util.Arrays.stream(palette).filter(m -> !uniform || m != original).toList();
        Material material = choices.get(random.nextInt(choices.size()));
        for (var c : cells) {
            Block b = world.getBlockAt(c.x(), c.y(), c.z());
            // Preserve stored loot before replacing container blocks.
            if (b.getState() instanceof InventoryHolder holder) {
                for (var item : holder.getInventory().getContents())
                    if (item != null && !item.getType().isAir()) world.dropItem(b.getLocation().add(.5, .5, .5), item.clone());
                holder.getInventory().clear();
            }
            changed(b); b.setType(material, false);
        }
        return cells.size();
    }
    private void restore(Lease l) { if (l.block().getBlockData().equals(l.placed())) l.block().setBlockData(l.original(), false); }
    void close() { for (Lease l : leases.values()) restore(l); leases.clear(); }
}
