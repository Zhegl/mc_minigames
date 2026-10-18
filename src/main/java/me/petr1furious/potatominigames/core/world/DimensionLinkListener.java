package me.petr1furious.potatominigames.core.world;

import java.util.Collection;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/**
 * Links throwaway worlds' dimensions; Bukkit would send portals to the primary world's.
 * HIGHEST to beat Multiverse-NetherPortals; carried state is re-applied a tick later so
 * inventory-splitting plugins cannot swallow items.
 */
public final class DimensionLinkListener implements Listener {
    private final JavaPlugin plugin;
    private final ThrowawayWorldService worlds;

    public DimensionLinkListener(JavaPlugin plugin, ThrowawayWorldService worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    private record CarriedState(ItemStack[] contents, ItemStack[] armor, ItemStack offHand,
            int level, float exp, double health, int food, float saturation,
            Collection<PotionEffect> effects) {

        static CarriedState of(Player player) {
            return new CarriedState(
                    player.getInventory().getContents().clone(),
                    player.getInventory().getArmorContents().clone(),
                    player.getInventory().getItemInOffHand().clone(),
                    player.getLevel(),
                    player.getExp(),
                    player.getHealth(),
                    player.getFoodLevel(),
                    player.getSaturation(),
                    List.copyOf(player.getActivePotionEffects()));
        }

        void apply(Player player) {
            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offHand);
            player.setLevel(level);
            player.setExp(exp);
            player.setHealth(Math.min(health, player.getAttribute(
                    org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.addPotionEffects(effects);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        World from = event.getFrom().getWorld();
        String base = worlds.baseOf(from);
        if (base == null) {
            return;
        }

        World target = resolveTarget(base, from, event.getCause());
        if (target == null) {
            return;
        }

        event.setTo(destinationIn(target, event.getFrom(), event.getTo(), event.getCause()));
        event.setCanCreatePortal(true);
        event.setSearchRadius(128);
        event.setCreationRadius(16);
        carryState(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        World from = event.getFrom().getWorld();
        String base = worlds.baseOf(from);
        if (base == null) {
            return;
        }
        // Entities only ever use nether portals to leave a dimension.
        World target = from.getEnvironment() == World.Environment.NETHER
                ? worlds.dimension(base, World.Environment.NORMAL)
                : worlds.getOrCreateDimension(base, World.Environment.NETHER);
        if (target == null) {
            return;
        }
        event.setTo(destinationIn(target, event.getFrom(), event.getTo(),
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL));
        event.setSearchRadius(128);
    }

    /** Keeps deaths and end exits out of the survival world. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        String base = worlds.baseOf(event.getPlayer().getWorld());
        if (base == null) {
            return;
        }
        World overworld = worlds.dimension(base, World.Environment.NORMAL);
        if (overworld == null) {
            return;
        }
        World respawnWorld = event.getRespawnLocation().getWorld();
        if (respawnWorld == null || worlds.baseOf(respawnWorld) == null) {
            event.setRespawnLocation(overworld.getSpawnLocation());
        }
    }

    private World resolveTarget(String base, World from, PlayerTeleportEvent.TeleportCause cause) {
        return switch (cause) {
            case NETHER_PORTAL -> from.getEnvironment() == World.Environment.NETHER
                    ? worlds.dimension(base, World.Environment.NORMAL)
                    : worlds.getOrCreateDimension(base, World.Environment.NETHER);
            case END_PORTAL -> from.getEnvironment() == World.Environment.THE_END
                    ? worlds.dimension(base, World.Environment.NORMAL)
                    : worlds.getOrCreateDimension(base, World.Environment.THE_END);
            default -> null;
        };
    }

    /** Keeps vanilla's computed coordinates and swaps the world; end travel uses vanilla anchors. */
    private Location destinationIn(World target, Location from, Location computed,
            PlayerTeleportEvent.TeleportCause cause) {
        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return target.getEnvironment() == World.Environment.THE_END
                    ? new Location(target, 100.5, 49, 0.5)
                    : target.getSpawnLocation();
        }
        Location base = computed != null ? computed.clone() : from.clone();
        base.setWorld(target);
        return base;
    }

    private void carryState(Player player) {
        CarriedState state = CarriedState.of(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                state.apply(player);
            }
        });
    }
}
