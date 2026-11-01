package me.petr1furious.potatominigames.core.game;

import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import me.petr1furious.potatominigames.core.team.TeamColor;

/**
 * One play-through of a minigame. Only events involving its players or worlds are
 * forwarded, and only while active.
 */
public interface GameSession {

    /** Also called for a knocked-out participant who is put back in. */
    default void onParticipantJoin(Player player) {
    }

    /** False lets {@code join} put a knocked-out participant back in. */
    default boolean isInGame(Player player) {
        return true;
    }

    default void onParticipantLeave(Player player) {
    }

    /** The participant record is kept. */
    default void onParticipantQuit(Player player) {
    }

    default void onParticipantRejoin(Player player) {
    }

    /**
     * Participants are already snapshotted. Throw
     * {@link me.petr1furious.potatominigames.core.command.CommandFailure} to abort.
     */
    void onStart() throws me.petr1furious.potatominigames.core.command.CommandFailure;

    /** Every second while RUNNING. */
    default void onSecondTick(long secondsElapsed) {
    }

    /** Announce results; core restores players and HUDs afterwards. */
    default void onEnd(EndReason reason) {
    }

    /** Null: core picks a random active player. */
    default org.bukkit.Location spectatorLocation(Player viewer) {
        return null;
    }

    /** Server-wide result line, e.g. "Alice won Pillars"; null for a generic one. */
    default net.kyori.adventure.text.Component globalResult() {
        return null;
    }

    /** Result lines for the end-of-game summary, read once {@link #onEnd} has run. */
    default java.util.List<net.kyori.adventure.text.Component> summary() {
        return java.util.List.of();
    }

    default boolean allowLateJoin() {
        return false;
    }

    /** False for games fixed at start, e.g. chase schedules every round's hunters. */
    default boolean allowLeave() {
        return true;
    }

    default boolean arePlayersFriendly(Player first, Player second) {
        return false;
    }

    /** Used by gadgets and HUDs. */
    default Collection<Player> getTrackedPlayers(Player viewer) {
        return List.of();
    }

    default boolean shouldHideTrackedPlayerNames(Player viewer) {
        return false;
    }

    /**
     * Whether a participant in spectator mode is out and may watch the others' view (see
     * {@code spectate.followPlayers}); spectators who are not playing always may. False for
     * someone only briefly out, e.g. waiting to respawn, who should not scout.
     */
    default boolean mayFollowPlayers(Player participant) {
        return !isInGame(participant);
    }

    default void onTeamChange(Player player, TeamColor team) {
    }

    // Event hooks, forwarded only while RUNNING.

    default void onPlayerRespawn(PlayerRespawnEvent event) {
    }

    default void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
    }

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

    default void onEntityDeath(EntityDeathEvent event) {
    }

    default void onEntityMount(EntityMountEvent event) {
    }

    default void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
    }

    default void onInventoryOpen(InventoryOpenEvent event) {
    }

    default void onEntityShootBow(EntityShootBowEvent event) {
    }

    default void onProjectileLaunch(ProjectileLaunchEvent event) {
    }

    default void onProjectileHit(ProjectileHitEvent event) {
    }

    default void onPotionSplash(PotionSplashEvent event) {
    }

    default void onFoodLevelChange(FoodLevelChangeEvent event) {
    }

    default void onPlayerInteract(PlayerInteractEvent event) {
    }

    default void onPlayerMove(PlayerMoveEvent event) {
    }

    default void onPlayerJump(PlayerJumpEvent event) {
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
