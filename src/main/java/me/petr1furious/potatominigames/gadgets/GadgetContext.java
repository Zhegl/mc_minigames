package me.petr1furious.potatominigames.gadgets;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Decouples gadgets from any specific game: friendliness, targeting and name
 * masking come from the active session when one is running, with sensible
 * defaults outside games.
 */
public interface GadgetContext {

    Plugin plugin();

    boolean gameRunning();

    boolean friendly(Player first, Player second);

    /** Players the user's gadgets may target (tracked enemies in-game; other players otherwise). */
    Collection<Player> targetsFor(Player user);

    boolean hideTargetNames(Player viewer);
}
