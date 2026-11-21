package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
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

/** Reflects a cube through its local X=Y plane. The Z coordinate stays fixed. */
final class PerkProtractor {
    private static final int RADIUS = 3;
    private static final double AIM_RANGE = 32;
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
        return new Pos(center.x() + cell.y() - center.y(), center.y() + cell.x() - center.x(), cell.z());
    }

    static Location transpose(Location at, Pos center) {
        double delta = center.x() - center.y();
        return new Location(at.getWorld(), at.getY() + delta, at.getX() - delta, at.getZ(), at.getYaw(), at.getPitch());
    }

    static boolean cast(Player player, PerkTerrain terrain, double deathHeight, Predicate<Entity> movable) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        var hit = world.rayTrace(eye, eye.getDirection(), AIM_RANGE, FluidCollisionMode.NEVER, true, .1,
                entity -> !entity.equals(player) && movable.test(entity));
        if (hit == null) return false;
        Block target = hit.getHitBlock() != null ? hit.getHitBlock() : hit.getHitEntity().getLocation().getBlock();
        Pos center = Pos.of(target);
        if (center.y() - RADIUS < deathHeight) return false;

        Map<Pos, BlockState> plan = new LinkedHashMap<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = dx + 1; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    Pos firstPos = new Pos(center.x() + dx, center.y() + dy, center.z() + dz);
                    Pos secondPos = transpose(firstPos, center);
                    Block first = block(world, firstPos), second = block(world, secondPos);
                    if (!available(world, terrain, first) || !available(world, terrain, second)) return false;
                    if (PerkTerrain.air(first) && PerkTerrain.air(second)) continue;
                    BlockState a = first.getState(), b = second.getState();
                    if (!(a instanceof TileState) && !(b instanceof TileState) && a.getBlockData().equals(b.getBlockData())) continue;
                    plan.put(firstPos, b.copy(first.getLocation()));
                    plan.put(secondPos, a.copy(second.getLocation()));
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
            if (!area.contains(anchor)) continue;
            // Transpose the centre so upright entities retain clearance after a vertical surface moves beside them.
            Vector moved = transpose(anchor.toLocation(world), center).toVector().subtract(anchor);
            Location to = from.clone().add(moved);
            if (to.getY() < deathHeight || !fits(world, plan, bounds.shift(moved))) return false;
            if (moved.lengthSquared() < 1e-12) continue;
            if (entity.isInsideVehicle() || !entity.getPassengers().isEmpty() || entity instanceof Hanging) return false;
            moves.add(new Move(entity, from, to, entity.getVelocity(), entity.getFallDistance()));
        }
        if (plan.isEmpty() && moves.isEmpty()) return false;
        for (int i = 0; i < moves.size(); i++) {
            if (moves.get(i).apply()) continue;
            for (int previous = i - 1; previous >= 0; previous--) moves.get(previous).undo();
            return false;
        }
        for (BlockState state : plan.values()) state.update(true, false);
        return true;
    }

    private static Block block(World world, Pos pos) { return world.getBlockAt(pos.x(), pos.y(), pos.z()); }

    private static boolean available(World world, PerkTerrain terrain, Block block) {
        return world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                && terrain.inside(block) && !terrain.leased(block);
    }

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
