package me.petr1furious.potatominigames.games.pillars;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/** Shared block visuals over real air: neither clients nor the server get a solid block. */
final class PerkIllusions implements AutoCloseable {
    private record Fake(BlockDisplay display, UUID owner) { }
    private record Hit(Block block, BlockFace face, PerkTerrain.Pos fake) { }
    private final World world;
    private final org.bukkit.block.data.BlockData appearance;
    private final Map<PerkTerrain.Pos, Fake> blocks = new HashMap<>();
    PerkIllusions(World world) { this(world, Material.OAK_PLANKS.createBlockData()); }
    PerkIllusions(World world, org.bukkit.block.data.BlockData appearance) { this.world = world; this.appearance = appearance.clone(); }
    private Hit target(Player p) {
        var eye = p.getEyeLocation();
        var start = eye.toVector();
        var real = p.rayTraceBlocks(PerkRules.BUILD_RANGE, FluidCollisionMode.NEVER);
        double distance = real == null ? Double.POSITIVE_INFINITY : real.getHitPosition().distanceSquared(start);
        Hit result = real == null ? null : new Hit(real.getHitBlock(), real.getHitBlockFace(), null);
        for (var entry : blocks.entrySet()) {
            var pos = entry.getKey();
            if (!entry.getValue().display().isValid()) continue;
            var ray = new BoundingBox(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1)
                    .rayTrace(start, eye.getDirection(), PerkRules.BUILD_RANGE);
            if (ray == null) continue;
            double hitDistance = ray.getHitPosition().distanceSquared(start);
            if (hitDistance < distance) {
                distance = hitDistance;
                result = new Hit(world.getBlockAt(pos.x(), pos.y(), pos.z()), ray.getHitBlockFace(), pos);
            }
        }
        return result;
    }
    boolean place(Player p) {
        if (blocks.size() >= 256 || blocks.values().stream().filter(b -> b.owner().equals(p.getUniqueId())).count() >= 64) return false;
        Hit hit = target(p);
        if (hit == null || hit.face() == null) return false;
        Block at = hit.block().getRelative(hit.face());
        if (at.getY() < world.getMinHeight() || at.getY() >= world.getMaxHeight()
                || !PerkTerrain.air(at) || blocks.containsKey(PerkTerrain.Pos.of(at))
                || !world.getWorldBorder().isInside(at.getLocation())) return false;
        BlockDisplay display = world.spawn(at.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(appearance.clone()); d.setPersistent(false); d.setInvulnerable(true);
            d.setViewRange(2);
        });
        if (!display.isValid()) return false;
        blocks.put(PerkTerrain.Pos.of(at), new Fake(display, p.getUniqueId()));
        world.playSound(at.getLocation(), "minecraft:block.wood.place", 1, 1);
        return true;
    }
    boolean breakAt(Player p) {
        Hit hit = target(p);
        if (hit == null || hit.fake() == null) return false;
        remove(hit.fake());
        world.playSound(hit.block().getLocation(), "minecraft:block.wood.break", 1, 1);
        return true;
    }
    private void remove(PerkTerrain.Pos pos) {
        Fake fake = blocks.remove(pos);
        if (fake != null) fake.display().remove();
    }
    void tick() {
        var it = blocks.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next(); var pos = entry.getKey();
            if (!entry.getValue().display().isValid() || !PerkTerrain.air(world.getBlockAt(pos.x(), pos.y(), pos.z()))) {
                entry.getValue().display().remove(); it.remove();
            }
        }
    }
    void playerGone(UUID owner) {
        var it = blocks.values().iterator();
        while (it.hasNext()) { Fake fake = it.next(); if (fake.owner().equals(owner)) { fake.display().remove(); it.remove(); } }
    }
    @Override public void close() {
        for (Fake fake : blocks.values()) fake.display().remove();
        blocks.clear();
    }
}
