package me.petr1furious.potatominigames.core.lobby;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.player.PlayerSnapshot;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.session.SessionManager;

/**
 * The lobby world players queue in, and the rules for getting in and out of it
 * and of games without losing anything.
 *
 * <p>Every player has at most one snapshot: their state from before the lobby or
 * a game, saved to disk. Entering the lobby takes one (if there is none) and puts
 * them in the lobby state — adventure mode, empty inventory, the menu item;
 * leaving it on their own gives everything back where they arrive. Joining a queue
 * or a game also takes one, and remembers where they came from: players who
 * queued in the lobby come back to the lobby, everyone else is restored.
 *
 * <p>Admins are never put into the lobby state (someone has to build it), but the
 * lobby still keeps them from taking damage or going hungry.
 */
public final class LobbyService implements Listener {
    /** Where a player goes once their game or queue is over. */
    private enum Target {
        /** Back to the lobby, still holding the snapshot from before it. */
        LOBBY,
        /** Restore the snapshot, wherever it was taken. */
        RESTORE
    }

    private final JavaPlugin plugin;
    private final PlayerStateService playerState;
    private final SessionManager sessions;
    private final NamespacedKey menuKey;

    private final PropertyBag config = new PropertyBag("lobby");
    private final Property<Boolean> enabled = config.define("enabled", PropertyCodec.BOOL, true);
    private final Property<String> worldName = config.define("world", PropertyCodec.STRING, "lobby");
    private final Property<Boolean> firstJoinToLobby = config.define("firstJoinToLobby", PropertyCodec.BOOL, true);
    private final Property<Integer> menuSlot = config.define("menuSlot", PropertyCodec.INT, 4);

    private final Map<UUID, Target> targets = new HashMap<>();
    /** Where a player stood before a world change, recorded by the teleport that caused it. */
    private final Map<UUID, Location> departedFrom = new HashMap<>();

    private Consumer<Player> menuOpener = player -> {
    };

    public LobbyService(JavaPlugin plugin, PlayerStateService playerState, SessionManager sessions) {
        this.plugin = plugin;
        this.playerState = playerState;
        this.sessions = sessions;
        this.menuKey = new NamespacedKey(plugin, "lobby_menu");
    }

    public PropertyBag config() {
        return config;
    }

    public void setMenuOpener(Consumer<Player> menuOpener) {
        this.menuOpener = menuOpener;
    }

    // --- the world ---

    /** The lobby world, or null when the lobby is disabled or its world is not loaded. */
    public World world() {
        return enabled.get() ? Bukkit.getWorld(worldName.get()) : null;
    }

    public boolean isLobby(World world) {
        World lobby = world();
        return lobby != null && lobby.equals(world);
    }

    /** The lobby world's spawn, moved up onto the ground if the stored spawn is inside a block. */
    public Location spawn() {
        World lobby = world();
        if (lobby == null) {
            return null;
        }
        Location spawn = lobby.getSpawnLocation().toCenterLocation().add(0, -0.5, 0);
        if (!spawn.getBlock().isPassable() || !spawn.getBlock().getRelative(0, 1, 0).isPassable()) {
            spawn.setY(lobby.getHighestBlockYAt(spawn) + 1);
        }
        return spawn;
    }

    public static boolean isExempt(Player player) {
        return player.hasPermission(GameCommands.ADMIN_PERMISSION);
    }

    /** Teleports a player to the lobby; false when there is no lobby to go to. */
    public boolean sendToLobby(Player player) {
        Location spawn = spawn();
        if (spawn == null) {
            return false;
        }
        player.teleport(spawn);
        // Already in the lobby world, the world-change listener does not fire.
        applyLobbyState(player);
        return true;
    }

    // --- queue and game flow ---

    /**
     * A player joins a queue: remember where they came from and bring them to the
     * lobby. Players already in the lobby will come back to it.
     */
    public void enterQueue(Player player) {
        UUID id = player.getUniqueId();
        if (isLobby(player.getWorld())) {
            if (isExempt(player)) {
                // Admins have no lobby snapshot; take one so their own things come back.
                playerState.snapshotIfAbsent(player);
            }
            targets.putIfAbsent(id, Target.LOBBY);
            return;
        }
        // A spectator moving on to a queue keeps the return they already had.
        if (playerState.snapshotIfAbsent(player)) {
            targets.put(id, Target.RESTORE);
        } else {
            targets.putIfAbsent(id, Target.RESTORE);
        }
        sendToLobby(player);
    }

    /**
     * A player leaves their queue before it started: players who came from outside
     * go back there; players who queued in the lobby simply stay.
     */
    public void leaveQueue(Player player) {
        UUID id = player.getUniqueId();
        if (targets.get(id) == Target.LOBBY) {
            targets.remove(id);
            if (isExempt(player)) {
                // The snapshot taken for the queue is just what they still have on them.
                playerState.forget(id);
            }
            return;
        }
        returnPlayer(player);
    }

    /**
     * A player is about to be put into a game (as a player or a spectator). Makes
     * sure there is a snapshot to give back, and decides where they return to unless
     * their queue already did.
     */
    public void prepareForGame(Player player) {
        UUID id = player.getUniqueId();
        boolean inLobby = isLobby(player.getWorld());
        if (playerState.snapshotIfAbsent(player)) {
            // A fresh snapshot is of where they are now, which is where they go back to.
            targets.put(id, inLobby ? Target.LOBBY : Target.RESTORE);
            return;
        }
        targets.putIfAbsent(id, inLobby ? Target.LOBBY : Target.RESTORE);
    }

    /**
     * A queued game failed to start: the player stays queued, so they go back to the
     * lobby (or stay there) still holding their snapshot and their way back.
     */
    public void backToQueue(Player player) {
        if (isLobby(player.getWorld())) {
            applyLobbyState(player);
        } else if (!sendToLobby(player)) {
            returnPlayer(player);
        }
    }

    /** The queue or game is over for this player: send them where they belong. */
    public void returnPlayer(Player player) {
        Target target = targets.remove(player.getUniqueId());
        if (target == Target.LOBBY && !isExempt(player) && sendToLobby(player)) {
            return;
        }
        if (playerState.restore(player)) {
            return;
        }
        // Nothing to give back (e.g. the snapshot was already used): the lobby is the
        // safe place to put somebody who just left a game world.
        sendToLobby(player);
    }

    /** Whether leaving the queue should send this player back out of the lobby. */
    public boolean returnsToLobby(Player player) {
        return targets.get(player.getUniqueId()) == Target.LOBBY;
    }

    public void forgetTarget(UUID playerId) {
        targets.remove(playerId);
    }

    // --- the lobby state ---

    /** Adventure mode, a clean inventory and the menu item; admins are left alone. */
    public void applyLobbyState(Player player) {
        if (isExempt(player) || !isLobby(player.getWorld())) {
            return;
        }
        playerState.resetForRound(player, GameMode.ADVENTURE);
        player.setSaturation(20.0f);
        player.getInventory().setItem(menuSlot.get(), menuItem());
        player.getInventory().setHeldItemSlot(menuSlot.get());
    }

    public ItemStack menuItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Play a game", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(Component.text("Right-click to pick a game", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(menuKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    private boolean isMenuItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.BYTE);
    }

    // --- getting in and out ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            departedFrom.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Location from = departedFrom.remove(player.getUniqueId());
        if (from == null) {
            from = event.getFrom().getSpawnLocation();
        }
        boolean wasInLobby = isLobby(event.getFrom());
        boolean nowInLobby = isLobby(player.getWorld());
        if (nowInLobby && !wasInLobby) {
            if (!isExempt(player) && !playerState.hasSnapshot(player.getUniqueId())) {
                // Snapshotted after arriving, but remembering where they came from.
                playerState.put(player.getUniqueId(), PlayerSnapshot.of(player).withLocation(from));
            }
            applyLobbyState(player);
        } else if (wasInLobby && !nowInLobby) {
            if (sessions.sessionOf(player).map(session -> session.owns(player.getWorld())).orElse(false)) {
                // Into their game, which takes it from here.
                return;
            }
            // Walking out on their own: their things come back, but they stay where they went.
            if (!isExempt(player) && playerState.restoreInPlace(player)) {
                targets.remove(player.getUniqueId());
            }
        }
    }

    /** Handles a login once the session manager has had its turn. */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (sessions.handleJoin(player)) {
            return;
        }
        // Teleporting inside the join event is unreliable, hence the tick of delay.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || sessions.isBusy(player)) {
                return;
            }
            settleAfterLogin(player);
        });
    }

    private void settleAfterLogin(Player player) {
        UUID id = player.getUniqueId();
        if (targets.containsKey(id)) {
            // Their game or queue ended while they were away.
            returnPlayer(player);
            return;
        }
        if (isLobby(player.getWorld())) {
            if (!isExempt(player)) {
                playerState.snapshotIfAbsent(player);
            }
            applyLobbyState(player);
            return;
        }
        if (playerState.hasSnapshot(id)) {
            // A game ended without them after a restart: give their things back.
            playerState.restore(player);
            return;
        }
        if (!player.hasPlayedBefore() && firstJoinToLobby.get()) {
            sendToLobby(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        departedFrom.remove(event.getPlayer().getUniqueId());
    }

    // --- protections ---

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && isLobby(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (isLobby(event.getEntity().getWorld()) && event.getFoodLevel() < event.getEntity().getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLobby(event.getPlayer().getWorld()) && !isExempt(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        World world = event.getTo().getWorld();
        if (event.getTo().getY() < world.getMinHeight() - 8 && isLobby(world)) {
            Location spawn = spawn();
            if (spawn != null) {
                event.setTo(spawn);
                event.getPlayer().setFallDistance(0);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isMenuItem(event.getItem())) {
            event.setCancelled(true);
            menuOpener.accept(event.getPlayer());
        }
    }
}
