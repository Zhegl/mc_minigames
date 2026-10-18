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
 * Lobby world and player state around queues and games.
 *
 * <p>Each player has at most one on-disk snapshot, taken on entering the lobby, a
 * queue or a game. Players who queued in the lobby return to it; others are restored.
 * Admins are not put into the lobby state but are still protected from damage and hunger.
 */
public final class LobbyService implements Listener {
    private enum Target {
        /** Back to the lobby, keeping the pre-lobby snapshot. */
        LOBBY,
        RESTORE
    }

    private final JavaPlugin plugin;
    private final PlayerStateService playerState;
    private final SessionManager sessions;
    private final NamespacedKey menuKey;

    private final PropertyBag config = new PropertyBag("lobby");
    private final Property<Boolean> enabled = config.define("enabled", PropertyCodec.BOOL, true);
    private final Property<String> worldName = config.define("world", PropertyCodec.STRING, "lobby")
            .suggest(() -> Bukkit.getWorlds().stream().map(World::getName).toList());
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

    /** Null when the lobby is disabled or not loaded. */
    public World world() {
        return enabled.get() ? Bukkit.getWorld(worldName.get()) : null;
    }

    public boolean isLobby(World world) {
        World lobby = world();
        return lobby != null && lobby.equals(world);
    }

    /** Moved up if the stored spawn is inside a block. */
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

    public boolean sendToLobby(Player player) {
        Location spawn = spawn();
        if (spawn == null) {
            return false;
        }
        player.teleport(spawn);
        // No world change, so the listener will not fire.
        applyLobbyState(player);
        return true;
    }

    public void enterQueue(Player player) {
        if (world() == null) {
            // No lobby: the game snapshots them when it starts.
            return;
        }
        UUID id = player.getUniqueId();
        if (isLobby(player.getWorld())) {
            if (isExempt(player)) {
                // Admins have no lobby snapshot.
                playerState.snapshotIfAbsent(player);
            }
            targets.putIfAbsent(id, Target.LOBBY);
            return;
        }
        // A spectator joining a queue keeps their existing return.
        if (playerState.snapshotIfAbsent(player)) {
            targets.put(id, Target.RESTORE);
        } else {
            targets.putIfAbsent(id, Target.RESTORE);
        }
        sendToLobby(player);
    }

    /** Players who came from outside the lobby are restored; the rest stay. */
    public void leaveQueue(Player player) {
        UUID id = player.getUniqueId();
        Target target = targets.get(id);
        if (target == null) {
            // No lobby, so queueing never moved them.
            return;
        }
        if (target == Target.LOBBY) {
            targets.remove(id);
            if (isExempt(player)) {
                // The queue snapshot is what they still carry.
                playerState.forget(id);
            }
            return;
        }
        returnPlayer(player);
    }

    /** Ensures a snapshot exists and sets the return unless the queue already did. */
    public void prepareForGame(Player player) {
        UUID id = player.getUniqueId();
        boolean inLobby = isLobby(player.getWorld());
        if (playerState.snapshotIfAbsent(player)) {
            targets.put(id, inLobby ? Target.LOBBY : Target.RESTORE);
            return;
        }
        targets.putIfAbsent(id, inLobby ? Target.LOBBY : Target.RESTORE);
    }

    /** The player stays queued and keeps their snapshot and return. */
    public void backToQueue(Player player) {
        if (isLobby(player.getWorld())) {
            applyLobbyState(player);
        } else if (!sendToLobby(player)) {
            returnPlayer(player);
        }
    }

    public void returnPlayer(Player player) {
        Target target = targets.remove(player.getUniqueId());
        if (target == Target.LOBBY && !isExempt(player) && sendToLobby(player)) {
            return;
        }
        if (playerState.restore(player)) {
            return;
        }
        // No snapshot left: fall back to the lobby.
        sendToLobby(player);
    }

    public boolean returnsToLobby(Player player) {
        return targets.get(player.getUniqueId()) == Target.LOBBY;
    }

    public void forgetTarget(UUID playerId) {
        targets.remove(playerId);
    }

    /** Admins keep their inventory and mode, and only get the menu item if they have none. */
    public void applyLobbyState(Player player) {
        if (!isLobby(player.getWorld())) {
            return;
        }
        if (isExempt(player)) {
            if (java.util.Arrays.stream(player.getInventory().getContents()).noneMatch(this::isMenuItem)) {
                if (player.getInventory().getItem(menuSlot.get()) == null) {
                    player.getInventory().setItem(menuSlot.get(), menuItem());
                } else {
                    player.getInventory().addItem(menuItem());
                }
            }
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
                // Snapshot taken after arriving, with the origin remembered.
                playerState.put(player.getUniqueId(), PlayerSnapshot.of(player).withLocation(from));
            }
            applyLobbyState(player);
        } else if (wasInLobby && !nowInLobby) {
            if (sessions.isBusy(player)) {
                // The game handles it.
                return;
            }
            // Left on their own: restore items, keep location.
            if (!isExempt(player) && playerState.restoreInPlace(player)) {
                targets.remove(player.getUniqueId());
            }
        }
    }

    /** Runs after the session manager's join handling. */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (sessions.handleJoin(player)) {
            return;
        }
        // Teleports in the join event are unreliable.
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
            // Game ended while offline across a restart.
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
