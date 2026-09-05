package me.petr1furious.potatominigames.core.world;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

public final class Locations {
    private Locations() {
    }

    /** True if a player can stand inside the two blocks at/above the location. Does not mutate the argument. */
    public static boolean playerPassable(Location location) {
        return location.getBlock().isPassable()
                && location.clone().add(0, 1, 0).getBlock().isPassable();
    }

    /**
     * The lowest standable spot at this column, scanning down from the given Y
     * (falling back to a scan from world top). Null if the column is all air.
     */
    public static Location getFirstSolidBlock(Location location) {
        var world = location.getWorld();
        var y = location.getBlockY();

        while (y > world.getMinHeight()) {
            if (!playerPassable(new Location(world, location.getBlockX(), y, location.getBlockZ()))) {
                break;
            }
            y--;
        }

        if (y == world.getMinHeight()) {
            y = world.getMaxHeight();
            while (y > world.getMinHeight()) {
                if (!playerPassable(new Location(world, location.getBlockX(), y, location.getBlockZ()))) {
                    break;
                }
                y--;
            }
        }

        if (y == world.getMinHeight()) {
            return null;
        }

        while (y < world.getMaxHeight()) {
            if (playerPassable(new Location(world, location.getBlockX(), y, location.getBlockZ()))) {
                break;
            }
            y++;
        }

        return new Location(world, location.getBlockX(), y, location.getBlockZ());
    }

    public static void teleportPlayerOnBlock(Player player) {
        var location = getFirstSolidBlock(player.getLocation());
        if (location != null) {
            location.add(0.5, 0, 0.5);
            location.setYaw(player.getLocation().getYaw());
            location.setPitch(player.getLocation().getPitch());
            player.teleport(location);
        }
    }

    public static void spawnExplosion(Location location, double explosionPower, Entity entity) {
        location.getWorld().createExplosion(entity, location, (float) explosionPower, false, true, false);
    }

    public static void spawnExplosion(Location location, double explosionPower, Entity entity,
            boolean excludeSourceFromDamage) {
        location.getWorld().createExplosion(entity, location, (float) explosionPower, false, true,
                excludeSourceFromDamage);
    }

    public static Entity getEntityShooter(Projectile projectile) {
        if (projectile.getShooter() instanceof Entity entity) {
            return entity;
        }
        return null;
    }
}
