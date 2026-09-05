package me.petr1furious.potatominigames.core.session;

import org.bukkit.entity.Player;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import me.petr1furious.potatominigames.core.game.GameSession;

/**
 * The only Bukkit listener for game logic. Every handler early-returns while no
 * session is active, keeping the plugin inert on an idle server.
 */
public final class GameplayListener implements Listener {
    private final SessionManager sessions;

    public GameplayListener(SessionManager sessions) {
        this.sessions = sessions;
    }

    // Events are also forwarded during ENDING so post-round UI (e.g. viewing the
    // final bingo board during the grace period) keeps working.
    private GameSession running() {
        GamePhase phase = sessions.phase();
        return phase == GamePhase.RUNNING || phase == GamePhase.ENDING
                ? sessions.activeSession().orElse(null)
                : null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sessions.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onPlayerDeath(event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onBlockBreak(event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onBlockPlace(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onEntityExplode(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onBlockExplode(event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onEntityDamageByEntity(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onEntityDamage(event);
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onEntityShootBow(event);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onPlayerInteract(event);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onPlayerMove(event);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onPlayerTeleport(event);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onInventoryClick(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onInventoryDrag(event);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onItemHeld(event);
        }
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onSwapHandItems(event);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onDropItem(event);
        }
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onPickupItem(event);
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onCraftItem(event);
        }
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        GameSession session = running();
        if (session != null) {
            session.onFurnaceExtract(event);
        }
    }
}
