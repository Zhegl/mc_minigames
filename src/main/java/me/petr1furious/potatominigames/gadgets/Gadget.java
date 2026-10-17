package me.petr1furious.potatominigames.gadgets;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import me.petr1furious.potatominigames.core.config.PropertyBag;

public interface Gadget {

    /** Stable identifier, used for the give command and the config section (e.g. "radar"). */
    String id();

    PropertyBag config();

    ItemStack createItem(int count);

    /**
     * Whether the gadget works for this player right now: they are playing a game, or
     * allowOutsideGame is set and they are not in the lobby (where gadgets never work).
     */
    boolean usableBy(Player player);

    default void onPlayerInteract(PlayerInteractEvent event) {
    }

    default void onBowShoot(EntityShootBowEvent event) {
    }

    /** @return true if this gadget consumed the impact (the projectile is handled). */
    default boolean handleProjectileImpact(Projectile projectile, Location location, Entity shooter,
            Entity target) {
        return false;
    }

    /** Called when a game session is cleaned up; despawn its players' entities and per-player state. */
    default void onGameEnd(java.util.Collection<java.util.UUID> players) {
    }
}
