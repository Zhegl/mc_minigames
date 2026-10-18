package me.petr1furious.potatominigames.gadgets;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Friendliness, targeting and name masking from the player's session, with defaults outside games. */
public interface GadgetContext {

    Plugin plugin();

    /** Playing, not spectating. */
    boolean inGame(Player player);

    /** Gadgets never work in the lobby. */
    boolean inLobby(Player player);

    boolean friendly(Player first, Player second);

    /** Tracked enemies in-game; other players otherwise. */
    Collection<Player> targetsFor(Player user);

    boolean hideTargetNames(Player viewer);
}
