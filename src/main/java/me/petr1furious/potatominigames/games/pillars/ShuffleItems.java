package me.petr1furious.potatominigames.games.pillars;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** A shuffle replaces all items in its arena, including items stashed outside player inventories. */
final class ShuffleItems implements Listener {
    private final World world;
    private final Map<Long, Long> blockCycles = new HashMap<>();
    private final Map<Long, Long> entityCycles = new HashMap<>();
    private final Map<UUID, Long> playerCycles = new HashMap<>();
    private long cycle;
    private boolean enabled = true;

    ShuffleItems(World world) {
        this.world = world;
    }

    /** Before the first temporary shuffle, leave normal inventories and world loot alone. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void nextCycle(Collection<Player> players) {
        cycle++;
        // Closing a view can return crafting ingredients or drop the cursor stack into the world.
        // Do it before sweeping the arena and before the session hands out the new set.
        for (Player player : players) {
            clearPlayerIfStale(player);
        }
        for (Chunk chunk : world.getLoadedChunks()) {
            clearBlocksIfStale(chunk);
            if (chunk.isEntitiesLoaded()) {
                clearEntitiesIfStale(chunk, Arrays.asList(chunk.getEntities()));
            }
        }
    }

    /** Reconnecting must not restore a set from an earlier shuffle. */
    void clearPlayerIfStale(Player player) {
        if (!enabled && cycle == 0) {
            return;
        }
        if (playerCycles.getOrDefault(player.getUniqueId(), -1L) == cycle) {
            return;
        }
        playerCycles.put(player.getUniqueId(), cycle);
        player.clearActiveItem();
        player.setItemOnCursor(null);
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top instanceof CraftingInventory) {
            top.clear();
        }
        player.closeInventory();
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        player.setItemOnCursor(null);
    }

    private void clearBlocksIfStale(Chunk chunk) {
        long key = chunk.getChunkKey();
        if (blockCycles.getOrDefault(key, -1L) == cycle) {
            return;
        }
        blockCycles.put(key, cycle);
        for (BlockState state : chunk.getTileEntities()) {
            // Only this chest half: do not load the adjacent chunk to resolve a double chest.
            if (state instanceof Chest chest) {
                chest.getBlockInventory().clear();
            } else if (state instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            } else if (state instanceof Campfire campfire) {
                for (int slot = 0; slot < campfire.getSize(); slot++) {
                    campfire.setItem(slot, null);
                }
                campfire.update(false, false);
            }
        }
    }

    private void clearEntitiesIfStale(Chunk chunk, Collection<Entity> entities) {
        long key = chunk.getChunkKey();
        if (entityCycles.getOrDefault(key, -1L) == cycle) {
            return;
        }
        entityCycles.put(key, cycle);
        for (Entity entity : entities) {
            // Only participant inventories are ours; an administrator may be visiting this world.
            if (entity instanceof Player) {
                continue;
            }
            // These can be broken/picked up to recover the original item after a shuffle.
            if (entity instanceof Item || entity instanceof Projectile || entity instanceof Boat
                    || entity instanceof Minecart || entity instanceof Hanging || entity instanceof ArmorStand) {
                entity.remove();
                continue;
            }
            if (entity instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            }
            if (entity instanceof LivingEntity living) {
                living.clearActiveItem();
                EntityEquipment equipment = living.getEquipment();
                if (equipment != null) {
                    equipment.clear();
                }
            }
        }
    }

    // A stash in an unloaded chunk must expire too, without force-loading the whole world.
    // Blocks and entities load separately; neither sweep may mark the other as already clean.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        if ((enabled || cycle > 0) && event.getWorld().equals(world)) {
            clearBlocksIfStale(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if ((enabled || cycle > 0) && event.getWorld().equals(world)) {
            clearEntitiesIfStale(event.getChunk(), event.getEntities());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer().getWorld().equals(world) && event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            // Ender chests belong to the persistent player, not the throwaway arena or its snapshot.
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("В шафле эндер-сундук недоступен: между наборами остаются только блоки.",
                    NamedTextColor.RED));
        }
    }

    void close() {
        HandlerList.unregisterAll(this);
        blockCycles.clear();
        entityCycles.clear();
        playerCycles.clear();
    }
}
