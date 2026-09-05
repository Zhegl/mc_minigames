package me.petr1furious.potatominigames.core.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/** Captures a world border so it can be restored after a game. */
public record BorderSnapshot(
        String worldName,
        double centerX,
        double centerZ,
        double size,
        double damageAmount,
        double damageBuffer,
        int warningDistance,
        int warningTimeTicks) {

    public static BorderSnapshot of(World world) {
        WorldBorder border = world.getWorldBorder();
        return new BorderSnapshot(
                world.getName(),
                border.getCenter().getX(),
                border.getCenter().getZ(),
                border.getSize(),
                border.getDamageAmount(),
                border.getDamageBuffer(),
                border.getWarningDistance(),
                border.getWarningTimeTicks());
    }

    /** Restores the border; no-op if the world is no longer loaded. */
    public void restore() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamageAmount(damageAmount);
        border.setDamageBuffer(damageBuffer);
        border.setWarningDistance(warningDistance);
        border.setWarningTimeTicks(warningTimeTicks);
    }
}
