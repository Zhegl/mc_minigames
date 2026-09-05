package me.petr1furious.potatominigames.core.game;

import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import me.petr1furious.potatominigames.core.team.TeamColor;

/**
 * One play-through of a minigame. Created when the lobby opens, discarded after cleanup.
 * All Bukkit events are forwarded here by the core listener only while the session is active.
 */
public interface GameSession {

    /** Called when a player late-joins a running game (see {@link #allowLateJoin()}). */
    default void onParticipantJoin(Player player) {
    }

    /** Called when a participant leaves for good via the leave command. */
    default void onParticipantLeave(Player player) {
    }

    /** Called when a participant disconnects mid-game. The participant record is kept. */
    default void onParticipantQuit(Player player) {
    }

    /** Called when a participant reconnects mid-game. */
    default void onParticipantRejoin(Player player) {
    }

    /**
     * Countdown finished; participants have been snapshotted. Teleport and prepare them here.
     * Throw {@link me.petr1furious.potatominigames.core.command.CommandFailure} to abort the start.
     */
    void onStart() throws me.petr1furious.potatominigames.core.command.CommandFailure;

    /** Driven by the core 1-second clock while RUNNING. */
    default void onSecondTick(long secondsElapsed) {
    }

    /** Announce results here. Player state restore and HUD cleanup happen afterwards in core. */
    default void onEnd(EndReason reason) {
    }

    default boolean allowLateJoin() {
        return false;
    }

    /**
     * Whether a participant may drop out mid-game. Games whose structure is fixed
     * when they start (chase schedules every round's hunters up front) say no.
     */
    default boolean allowLeave() {
        return true;
    }

    default boolean arePlayersFriendly(Player first, Player second) {
        return false;
    }

    /** Players the viewer is hunting/tracking; used by gadgets and HUDs. */
    default Collection<Player> getTrackedPlayers(Player viewer) {
        return List.of();
    }

    default boolean shouldHideTrackedPlayerNames(Player viewer) {
        return false;
    }

    /** A participant's team changed while the session is active. */
    default void onTeamChange(Player player, TeamColor team) {
    }

    // --- Bukkit event hooks (forwarded only while RUNNING) ---

    default void onPlayerDeath(PlayerDeathEvent event) {
    }

    default void onBlockBreak(BlockBreakEvent event) {
    }

    default void onBlockPlace(BlockPlaceEvent event) {
    }

    default void onEntityExplode(EntityExplodeEvent event) {
    }

    default void onBlockExplode(BlockExplodeEvent event) {
    }

    default void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    }

    default void onEntityDamage(EntityDamageEvent event) {
    }

    default void onEntityShootBow(EntityShootBowEvent event) {
    }

    default void onPlayerInteract(PlayerInteractEvent event) {
    }

    default void onPlayerMove(PlayerMoveEvent event) {
    }

    default void onPlayerTeleport(PlayerTeleportEvent event) {
    }

    default void onInventoryClick(InventoryClickEvent event) {
    }

    default void onInventoryDrag(InventoryDragEvent event) {
    }

    default void onItemHeld(PlayerItemHeldEvent event) {
    }

    default void onSwapHandItems(PlayerSwapHandItemsEvent event) {
    }

    default void onDropItem(PlayerDropItemEvent event) {
    }

    default void onPickupItem(EntityPickupItemEvent event) {
    }

    default void onCraftItem(CraftItemEvent event) {
    }

    default void onFurnaceExtract(FurnaceExtractEvent event) {
    }
}
