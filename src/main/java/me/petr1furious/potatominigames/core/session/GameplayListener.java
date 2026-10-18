package me.petr1furious.potatominigames.core.session;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import me.petr1furious.potatominigames.core.game.GameSession;

/** Routes events to the sessions of the involved players and of the world. */
public final class GameplayListener implements Listener {
    private final SessionManager sessions;

    public GameplayListener(SessionManager sessions) {
        this.sessions = sessions;
    }

    /** Also forwards during ENDING so post-round UI (e.g. the bingo board) works. */
    private void dispatch(World world, Consumer<GameSession> handler, Entity... involved) {
        if (sessions.activeSessions().isEmpty()) {
            return;
        }
        Set<ActiveSession> targets = new LinkedHashSet<>();
        for (Entity entity : involved) {
            Entity source = entity instanceof Projectile projectile
                    && projectile.getShooter() instanceof Entity shooter ? shooter : entity;
            if (source instanceof Player player) {
                sessions.sessionOf(player).ifPresent(targets::add);
            }
        }
        if (world != null) {
            sessions.sessionOwning(world).ifPresent(targets::add);
        }
        for (ActiveSession session : targets) {
            if (session.phase() != GamePhase.IDLE && session.gameSession() != null) {
                handler.accept(session.gameSession());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        dispatch(player.getWorld(), session -> session.onPlayerDeath(event), player, player.getKiller());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        dispatch(event.getBlock().getWorld(), session -> session.onBlockBreak(event), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        dispatch(event.getBlock().getWorld(), session -> session.onBlockPlace(event), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        dispatch(event.getLocation().getWorld(), session -> session.onEntityExplode(event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        dispatch(event.getBlock().getWorld(), session -> session.onBlockExplode(event));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        dispatch(event.getEntity().getWorld(), session -> session.onEntityDamageByEntity(event),
                event.getDamager(), event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        dispatch(event.getEntity().getWorld(), session -> session.onEntityDamage(event), event.getEntity());
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        dispatch(event.getEntity().getWorld(), session -> session.onEntityShootBow(event), event.getEntity());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        dispatch(event.getPlayer().getWorld(), session -> session.onPlayerInteract(event), event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        dispatch(event.getTo().getWorld(), session -> session.onPlayerMove(event), event.getPlayer());
    }

    /** Keeps spectator-menu teleports within the game's worlds. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        sessions.sessionOf(event.getPlayer()).ifPresent(session -> {
            if (!session.owns(event.getTo().getWorld())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Own session sees all their teleports; the destination's owner sees arrivals.
        dispatch(event.getTo().getWorld(), session -> session.onPlayerTeleport(event), event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        dispatch(event.getWhoClicked().getWorld(), session -> session.onInventoryClick(event),
                event.getWhoClicked());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        dispatch(event.getWhoClicked().getWorld(), session -> session.onInventoryDrag(event),
                event.getWhoClicked());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        dispatch(event.getPlayer().getWorld(), session -> session.onItemHeld(event), event.getPlayer());
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        dispatch(event.getPlayer().getWorld(), session -> session.onSwapHandItems(event), event.getPlayer());
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        dispatch(event.getPlayer().getWorld(), session -> session.onDropItem(event), event.getPlayer());
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        dispatch(event.getEntity().getWorld(), session -> session.onPickupItem(event), event.getEntity());
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        dispatch(event.getWhoClicked().getWorld(), session -> session.onCraftItem(event), event.getWhoClicked());
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        dispatch(event.getPlayer().getWorld(), session -> session.onFurnaceExtract(event), event.getPlayer());
    }
}
