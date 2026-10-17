package me.petr1furious.potatominigames.gadgets;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Decouples gadgets from any specific game: friendliness, targeting and name
 * masking come from the session the player is playing in, with sensible
 * defaults outside games.
 */
public interface GadgetContext {

    Plugin plugin();

    /** Whether the player is playing (not spectating) in a running game. */
    boolean inGame(Player player);

    /** Whether the player is in the lobby, where gadgets never work. */
    boolean inLobby(Player player);

    boolean friendly(Player first, Player second);

    /** Players the user's gadgets may target (tracked enemies in-game; other players otherwise). */
    Collection<Player> targetsFor(Player user);

    boolean hideTargetNames(Player viewer);
}
