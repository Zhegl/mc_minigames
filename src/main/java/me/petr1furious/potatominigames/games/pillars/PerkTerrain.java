package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.Scaffolding;
import org.bukkit.inventory.InventoryHolder;

/** Temporary structures preserve existing blocks; transmutation preflights the complete component. */
final class PerkTerrain {
    record Pos(int x, int y, int z) { static Pos of(Block b) { return new Pos(b.getX(), b.getY(), b.getZ()); } }
    private record Lease(Block block, BlockData original, BlockData placed, long until, boolean locked) { }
    private final World world;
    private final Map<Pos, Lease> leases = new HashMap<>();
    PerkTerrain(World world) { this.world = world; }
    boolean locked(Block b, long tick) { Lease l = leases.get(Pos.of(b)); return l != null && l.locked() && tick < l.until(); }
    boolean leased(Block block) { return leases.containsKey(Pos.of(block)); }
    void changed(Block b) { leases.remove(Pos.of(b)); }
    static boolean air(Block block) { return air(block.getType()); }
    static boolean air(Material material) { return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR; }
    boolean canLease(List<Block> blocks) {
        return blocks.size() + leases.size() <= 8192 && blocks.stream().allMatch(b -> inside(b) && air(b));
    }
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
    static Map<Pos, Material> bunkerPlan() {
        Map<Pos, Material> plan = new LinkedHashMap<>(housePlan());
        plan.replaceAll((pos, material) -> material == Material.OAK_DOOR ? Material.IRON_DOOR : Material.OBSIDIAN);
        plan.put(new Pos(0, -1, -2), Material.OBSIDIAN);
        plan.put(new Pos(0, 0, -2), Material.LEVER);
        plan.put(new Pos(0, 1, 0), Material.LEVER);
        plan.put(new Pos(0, 0, 0), Material.AIR);
        plan.put(new Pos(0, 1, -2), Material.AIR);
        return plan;
    }

    boolean house(Location at) { return build(at, housePlan(), true); }
    boolean bunker(Location at) { return build(at, bunkerPlan(), true); }
    boolean garden(Location at, BlockFace forward) { return build(at, PerkLayouts.garden(forward), false); }
    boolean maze(Location at) { return build(at, PerkLayouts.maze(), true); }

    boolean inside(Block block) {
        return block.getY() >= world.getMinHeight() && block.getY() < world.getMaxHeight()
                && world.getWorldBorder().isInside(block.getLocation());
    }

    boolean scaffold(Block base) {
        if (!base.getRelative(BlockFace.DOWN).getType().isSolid()) return false;
        for (int y = 0; y < 10; y++) {
            Block block = base.getRelative(0, y, 0);
            if (!inside(block) || !air(block)) return false;
        }
        Scaffolding data = (Scaffolding) Material.SCAFFOLDING.createBlockData();
        data.setDistance(0);
        for (int y = 0; y < 10; y++) base.getRelative(0, y, 0).setBlockData(data, false);
        return true;
    }

    void recolor(Block block, Material material) {
        Pos pos = Pos.of(block);
        Lease lease = leases.get(pos);
        if (lease == null || !block.getBlockData().equals(lease.placed())) return;
        block.setType(material, false);
        leases.put(pos, new Lease(block, lease.original(), block.getBlockData().clone(), lease.until(), lease.locked()));
    }

    private Block offset(Location at, Pos pos) {
        return world.getBlockAt(at.getBlockX() + pos.x(), at.getBlockY() + pos.y(), at.getBlockZ() + pos.z());
    }

    private boolean build(Location at, Map<Pos, Material> plan, boolean keepFloor) {
        // Preflight the whole structure before spending the item or changing any blocks.
        for (Pos pos : plan.keySet()) {
            Block block = offset(at, pos);
            if (!inside(block) || (!air(block) && !(keepFloor && pos.y() == -1))) return false;
        }
        var ordered = plan.entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getKey().y())).toList();
        for (var entry : ordered) {
            Pos pos = entry.getKey();
            Block block = offset(at, pos);
            Material material = entry.getValue();
            if (!air(block) || material.isAir()) continue;
            BlockData data = material.createBlockData();
            if (data instanceof Door door) {
                door.setFacing(BlockFace.NORTH);
                door.setHalf(pos.y() == 0 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            }
            if (data instanceof Directional direction && material == Material.WALL_TORCH)
                direction.setFacing(BlockFace.NORTH);
            if (data instanceof Switch lever) {
                lever.setAttachedFace(pos.z() == 0 ? FaceAttachable.AttachedFace.WALL : FaceAttachable.AttachedFace.FLOOR);
                lever.setFacing(pos.z() == 0 ? BlockFace.WEST : BlockFace.NORTH);
            }
            if (data instanceof Farmland farmland) farmland.setMoisture(farmland.getMaximumMoisture());
            block.setBlockData(data, false);
        }
        return true;
    }
    boolean pillar(Location at, Material material, RandomGenerator random) {
        return pillarSpot(at, material, random) != null;
    }

    Location pillarSpot(Location at, Material material, RandomGenerator random) {
        int top = at.getBlockY() - 1;
        int bottom = world.getMinHeight();
        if (top < bottom || top + 2 >= world.getMaxHeight()) return null;
        var offsets = new ArrayList<>(PerkLayouts.pillarOffsets());
        // Choose a free column at random, including two clear blocks above its top.
        while (!offsets.isEmpty()) {
            Pos offset = offsets.remove(random.nextInt(offsets.size()));
            int x = at.getBlockX() + offset.x(), z = at.getBlockZ() + offset.z();
            double distance = Math.hypot(x + .5 - at.getX(), z + .5 - at.getZ());
            if (distance < 5 || distance > 8) continue;
            if (!inside(world.getBlockAt(x, bottom, z))) continue;
            if (!air(world.getBlockAt(x, top + 2, z)) || !air(world.getBlockAt(x, top + 1, z))) continue;
            int y = top;
            while (y >= bottom && air(world.getBlockAt(x, y, z))) y--;
            if (y == top) continue;
            for (y++; y <= top; y++) world.getBlockAt(x, y, z).setType(material, false);
            return new Location(world, x + .5, top + 1, z + .5);
        }
        return null;
    }

    void tick(long tick) {
        var it = leases.values().iterator();
        while (it.hasNext()) { Lease lease = it.next(); if (tick >= lease.until()) { restore(lease); it.remove(); } }
    }
    int transmute(Block start, long tick, java.util.random.RandomGenerator random) {
        return transmute(start, tick, random, null);
    }
    int transmute(Block start, long tick, java.util.random.RandomGenerator random, Material selected) {
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
        Material material = selected == null ? choices.get(random.nextInt(choices.size())) : selected;
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
