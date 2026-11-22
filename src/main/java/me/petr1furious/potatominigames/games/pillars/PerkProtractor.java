package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import me.petr1furious.potatominigames.games.pillars.PerkTerrain.Pos;

/** Reflects a sphere through its local X=Z plane, with the centre at the caster's block. */
final class PerkProtractor {
    static final int RADIUS = 50;
    private record Move(Entity entity, Location from, Location to, Vector velocity, float fallDistance) {
        boolean apply() { return teleport(to); }
        void undo() { teleport(from); }

        private boolean teleport(Location location) {
            if (!entity.teleport(location)) return false;
            entity.setVelocity(velocity);
            entity.setFallDistance(fallDistance);
            return true;
        }
    }

    static Pos transpose(Pos cell, Pos center) {
        return new Pos(center.x() + cell.z() - center.z(), cell.y(), center.z() + cell.x() - center.x());
    }

    static Location transpose(Location at, Pos center) {
        double delta = center.x() - center.z();
        return new Location(at.getWorld(), at.getZ() + delta, at.getY(), at.getX() - delta, at.getYaw(), at.getPitch());
    }

    /** Returns the changed terrain cells, or null if the cast could not be applied. */
    static Set<Pos> cast(World world, Pos center, PerkTerrain terrain, Predicate<Entity> movable) {
        double radiusSquared = RADIUS * RADIUS;
        int minHeight = world.getMinHeight(), maxHeight = world.getMaxHeight();
        var border = world.getWorldBorder();
        Map<Pos, BlockState> plan = new LinkedHashMap<>();
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = dx + 1; dz <= RADIUS; dz++) {
                int remaining = RADIUS * RADIUS - dx * dx - dz * dz;
                if (remaining < 0) continue;
                int x = center.x() + dx, z = center.z() + dz;
                int otherX = center.x() + dz, otherZ = center.z() + dx;
                // Skip unmapped columns at the border, so casts near it still have an effect.
                if (!border.isInside(new Location(world, x + .5, center.y(), z + .5))
                        || !border.isInside(new Location(world, otherX + .5, center.y(), otherZ + .5))) continue;
                int height = (int) Math.sqrt(remaining);
                int minY = Math.max(minHeight, center.y() - height);
                int maxY = Math.min(maxHeight - 1, center.y() + height);
                ChunkSnapshot firstChunk = snapshot(world, snapshots, x, z);
                ChunkSnapshot secondChunk = snapshot(world, snapshots, otherX, otherZ);
                for (int y = minY; y <= maxY; y++) {
                    int section = (y - minHeight) >> 4;
                    if (firstChunk.isSectionEmpty(section) && secondChunk.isSectionEmpty(section)) {
                        y = minHeight + ((section + 1) << 4) - 1;
                        continue;
                    }
                    if (PerkTerrain.air(firstChunk.getBlockType(x & 15, y, z & 15))
                            && PerkTerrain.air(secondChunk.getBlockType(otherX & 15, y, otherZ & 15))) continue;
                    Block first = world.getBlockAt(x, y, z), second = world.getBlockAt(otherX, y, otherZ);
                    BlockState a = first.getState(), b = second.getState();
                    if (!(a instanceof TileState) && !(b instanceof TileState) && a.getBlockData().equals(b.getBlockData())) continue;
                    if (terrain.leased(first) || terrain.leased(second)) return null;
                    plan.put(new Pos(x, y, z), b.copy(first.getLocation()));
                    plan.put(new Pos(otherX, y, otherZ), a.copy(second.getLocation()));
                }
            }
        }

        BoundingBox area = new BoundingBox(center.x() - RADIUS, center.y() - RADIUS, center.z() - RADIUS,
                center.x() + RADIUS + 1, center.y() + RADIUS + 1, center.z() + RADIUS + 1);
        var moves = new ArrayList<Move>();
        for (Entity entity : world.getNearbyEntities(area, movable)) {
            Location from = entity.getLocation();
            BoundingBox bounds = entity.getBoundingBox();
            Vector anchor = bounds.getCenter();
            double dx = anchor.getX() - center.x() - .5;
            double dy = anchor.getY() - center.y() - .5;
            double dz = anchor.getZ() - center.z() - .5;
            if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
            // Reflect the centre while keeping the entity upright.
            Vector moved = transpose(anchor.toLocation(world), center).toVector().subtract(anchor);
            Location to = from.clone().add(moved);
            boolean displaced = moved.lengthSquared() >= 1e-12;
            if (!border.isInside(to) || !fits(world, plan, bounds.shift(moved))
                    || displaced && (entity.isInsideVehicle() || !entity.getPassengers().isEmpty() || entity instanceof Hanging)) {
                if (entity instanceof Player) return null;
                continue;
            }
            if (!displaced) continue;
            moves.add(new Move(entity, from, to, entity.getVelocity(), entity.getFallDistance()));
        }
        if (plan.isEmpty() && moves.isEmpty()) return null;
        for (int i = 0; i < moves.size(); i++) {
            if (moves.get(i).apply()) continue;
            for (int previous = i - 1; previous >= 0; previous--) moves.get(previous).undo();
            return null;
        }
        for (BlockState state : plan.values()) state.update(true, false);
        return plan.keySet();
    }

    private static ChunkSnapshot snapshot(World world, Map<Long, ChunkSnapshot> snapshots, int x, int z) {
        int chunkX = x >> 4, chunkZ = z >> 4;
        long key = ((long) chunkX << 32) | Integer.toUnsignedLong(chunkZ);
        return snapshots.computeIfAbsent(key, ignored ->
                world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false, false));
    }

    private static Block block(World world, Pos pos) { return world.getBlockAt(pos.x(), pos.y(), pos.z()); }

    private static boolean fits(World world, Map<Pos, BlockState> plan, BoundingBox bounds) {
        for (int x = Location.locToBlock(bounds.getMinX()); x < Math.ceil(bounds.getMaxX()); x++) {
            for (int y = Location.locToBlock(bounds.getMinY()); y < Math.ceil(bounds.getMaxY()); y++) {
                for (int z = Location.locToBlock(bounds.getMinZ()); z < Math.ceil(bounds.getMaxZ()); z++) {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
                    BlockState state = plan.get(new Pos(x, y, z));
                    if (state == null ? block(world, new Pos(x, y, z)).isCollidable() : state.isCollidable()) return false;
                }
            }
        }
        return true;
    }
}
