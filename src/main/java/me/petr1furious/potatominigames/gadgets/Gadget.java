package me.petr1furious.potatominigames.gadgets;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
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

    /** Whether the gadget works right now: a game is running, or allowOutsideGame is set. */
    boolean usableNow();

    default void onPlayerInteract(PlayerInteractEvent event) {
    }

    default void onBowShoot(EntityShootBowEvent event) {
    }

    /** @return true if this gadget consumed the impact (the projectile is handled). */
    default boolean handleProjectileImpact(Projectile projectile, Location location, Entity shooter,
            Entity target) {
        return false;
    }

    /** Called when a game session is cleaned up; despawn entities, cancel per-player state. */
    default void onGameEnd() {
    }
}
