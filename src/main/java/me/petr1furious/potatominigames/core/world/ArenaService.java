package me.petr1furious.potatominigames.core.world;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * World-level game setup shared by all minigames: border snapshot/restore and
 * the standard "no interference" game rules.
 */
public final class ArenaService {
    private final Map<String, BorderSnapshot> borderSnapshots = new HashMap<>();

    /** Snapshots the border (once per world until restored) and applies center + size. */
    public void applyBorder(World world, Location center, double initialSize, double finalSize,
            long secondsToFinal) {
        borderSnapshots.computeIfAbsent(world.getName(), name -> BorderSnapshot.of(world));
        WorldBorder border = world.getWorldBorder();
        border.setCenter(center);
        border.setSize(Math.max(1.0, initialSize));
        if (secondsToFinal > 0 && initialSize != finalSize) {
            border.changeSize(Math.max(1.0, finalSize), secondsToFinal);
        }
    }

    public void snapshotBorder(World world) {
        borderSnapshots.computeIfAbsent(world.getName(), name -> BorderSnapshot.of(world));
    }

    public void restoreBorder(World world) {
        BorderSnapshot snapshot = borderSnapshots.remove(world.getName());
        if (snapshot != null) {
            snapshot.restore();
        }
    }

    public void restoreAllBorders() {
        for (BorderSnapshot snapshot : borderSnapshots.values()) {
            snapshot.restore();
        }
        borderSnapshots.clear();
    }

    /** Keep-inventory on, mob/raid/phantom/patrol/trader spawns and drops off. */
    public void applyStandardGameRules(World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.BLOCK_DROPS, false);
        world.setGameRule(GameRules.MOB_DROPS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.RAIDS, false);
    }
}
