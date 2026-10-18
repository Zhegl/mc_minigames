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

    /** Used for the give command and config section, e.g. "radar". */
    String id();

    PropertyBag config();

    ItemStack createItem(int count);

    /** In a game, or allowOutsideGame and not in the lobby. */
    boolean usableBy(Player player);

    default void onPlayerInteract(PlayerInteractEvent event) {
    }

    default void onBowShoot(EntityShootBowEvent event) {
    }

    /** @return true if this gadget handled the impact */
    default boolean handleProjectileImpact(Projectile projectile, Location location, Entity shooter,
            Entity target) {
        return false;
    }

    /** Despawn the session players' entities and state. */
    default void onGameEnd(java.util.Collection<java.util.UUID> players) {
    }
}
