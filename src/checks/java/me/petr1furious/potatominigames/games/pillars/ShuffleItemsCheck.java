package me.petr1furious.potatominigames.games.pillars;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;

/** Exercise the real cleanup against inventories, entities and separate block/entity chunk loads. */
public final class ShuffleItemsCheck {
    public static void main(String[] args) {
        Arena arena = new Arena();
        ChunkState loaded = arena.chunk(true);
        Store chest = new Store();
        loaded.blocks.add(fake(Chest.class, (p, m, a) -> switch (m.getName()) {
            case "getBlockInventory" -> chest.inventory;
            default -> throw unexpected(m.getName());
        }));
        Store container = new Store();
        loaded.blocks.add(fake(Container.class, (p, m, a) -> switch (m.getName()) {
            case "getInventory" -> container.inventory;
            default -> throw unexpected(m.getName());
        }));
        boolean[] cooking = {true, true, true, true};
        loaded.blocks.add(fake(Campfire.class, (p, m, a) -> switch (m.getName()) {
            case "getSize" -> cooking.length;
            case "setItem" -> { check(a[1] == null, "Cooking items are discarded"); cooking[(int) a[0]] = false; yield null; }
            case "update" -> true;
            default -> throw unexpected(m.getName());
        }));
        StoredEntity dropped = new StoredEntity(Item.class);
        StoredEntity trident = new StoredEntity(Projectile.class);
        StoredEntity frame = new StoredEntity(ItemFrame.class);
        StoredEntity boat = new StoredEntity(Boat.class);
        StoredEntity minecart = new StoredEntity(Minecart.class);
        StoredEntity stand = new StoredEntity(ArmorStand.class);
        StoredEntity carrier = new StoredEntity(LivingEntity.class, InventoryHolder.class);
        loaded.entities.addAll(List.of(dropped.entity, trident.entity, frame.entity, boat.entity,
                minecart.entity, stand.entity, carrier.entity));

        PlayerState player = new PlayerState(arena.world, true);
        PlayerState visitor = new PlayerState(arena.world, true);
        loaded.entities.add(visitor.player);
        StoredEntity closeDrop = new StoredEntity(Item.class);
        player.onClose = () -> {
            player.inventory.occupied = true; // Vanilla can return ingredients when closing a view.
            loaded.entities.add(closeDrop.entity);
        };

        ShuffleItems cleanup = new ShuffleItems(arena.world);
        cleanup.nextCycle(List.of(player.player));
        check(!player.inventory.occupied && !player.top.occupied && !player.cursor,
                "Inventory, crafting grid and cursor cannot carry an old set over");
        check(!player.inventory.armour && !player.inventory.offHand, "Armour and offhand items expire too");
        check(player.closed && player.activeCleared, "Close views and cancel active item use before the new set");
        check(!chest.occupied && !container.occupied, "Chest halves and other block inventories are emptied");
        for (boolean item : cooking) check(!item, "Campfire cooking slots cannot save food");
        check(dropped.removed && trident.removed && closeDrop.removed, "Drops and recoverable projectiles expire");
        check(frame.removed && boat.removed && minecart.removed && stand.removed,
                "Placing frames, boats, minecarts or armour stands cannot preserve the original item");
        check(!carrier.store.occupied && !carrier.equipped, "Entity storage and equipment cannot stash old items");
        check(visitor.inventory.occupied && !visitor.closed, "Do not clear visiting administrators' inventories");
        // Block proxies reject any terrain mutation; clearing storage must leave the blocks themselves.

        chest.occupied = true;
        dropped.removed = false;
        cleanup.onChunkLoad(new ChunkLoadEvent(loaded.chunk, false));
        cleanup.onEntitiesLoad(new EntitiesLoadEvent(loaded.chunk, List.of(dropped.entity)));
        check(chest.occupied && !dropped.removed, "Same-cycle reloads preserve fresh items");
        cleanup.nextCycle(List.of(player.player));
        check(!chest.occupied && dropped.removed, "The following shuffle expires the next set too");

        ChunkState unloaded = arena.chunk(false);
        Store stash = new Store();
        unloaded.blocks.add(fake(Container.class, (p, m, a) -> switch (m.getName()) {
            case "getInventory" -> stash.inventory;
            default -> throw unexpected(m.getName());
        }));
        StoredEntity hiddenDrop = new StoredEntity(Item.class);
        cleanup.nextCycle(List.of());
        check(stash.occupied && !hiddenDrop.removed, "Do not force-load distant stashes");
        unloaded.loaded = true;
        cleanup.onChunkLoad(new ChunkLoadEvent(unloaded.chunk, false));
        check(!stash.occupied && !hiddenDrop.removed, "Block loading cleans storage independently of entities");
        cleanup.onEntitiesLoad(new EntitiesLoadEvent(unloaded.chunk, List.of(hiddenDrop.entity)));
        check(hiddenDrop.removed, "Entities loading later cannot resurrect old items");
        stash.occupied = true;
        hiddenDrop.removed = false;
        cleanup.onChunkLoad(new ChunkLoadEvent(unloaded.chunk, false));
        cleanup.onEntitiesLoad(new EntitiesLoadEvent(unloaded.chunk, List.of(hiddenDrop.entity)));
        check(stash.occupied && !hiddenDrop.removed, "Current-cycle items survive repeated load notifications");

        // Blocks may remain loaded while their entities are unloaded across another shuffle.
        unloaded.entitiesLoaded = false;
        unloaded.entities.add(hiddenDrop.entity);
        cleanup.nextCycle(List.of());
        check(!stash.occupied && !hiddenDrop.removed, "Loaded terrain does not force entity loading");
        cleanup.onEntitiesLoad(new EntitiesLoadEvent(unloaded.chunk, List.of(hiddenDrop.entity)));
        check(hiddenDrop.removed, "A later entity load still expires its stale items");

        PlayerState reconnecting = new PlayerState(arena.world, true);
        cleanup.clearPlayerIfStale(reconnecting.player);
        check(!reconnecting.inventory.occupied && !reconnecting.top.occupied, "Reconnect clears stale gear and grid");
        reconnecting.inventory.occupied = true;
        cleanup.clearPlayerIfStale(reconnecting.player);
        check(reconnecting.inventory.occupied, "A same-cycle reconnect keeps the current set");
        cleanup.nextCycle(List.of());
        cleanup.clearPlayerIfStale(reconnecting.player);
        check(!reconnecting.inventory.occupied, "Logging out across a shuffle cannot save gear");

        PlayerState ender = new PlayerState(arena.world, false);
        cleanup.clearPlayerIfStale(ender.player);
        check(ender.top.occupied, "Never erase a player's persistent ender chest");

        Arena other = new Arena();
        ChunkState foreignChunk = other.chunk(true);
        Store foreignChest = new Store();
        foreignChunk.blocks.add(fake(Container.class, (p, m, a) -> foreignChest.inventory));
        StoredEntity foreignDrop = new StoredEntity(Item.class);
        cleanup.onChunkLoad(new ChunkLoadEvent(foreignChunk.chunk, false));
        cleanup.onEntitiesLoad(new EntitiesLoadEvent(foreignChunk.chunk, List.of(foreignDrop.entity)));
        check(foreignChest.occupied && !foreignDrop.removed,
                "Other worlds are unaffected");
        cleanup.close();
        System.out.println("Shuffle: dropped and stashed items, crafting/cursor, entity storage, chunk reloads and reconnects verified");
    }

    private static final class Store {
        boolean occupied = true, armour = true, offHand = true;
        final Inventory inventory;
        Store() { this(false); }
        Store(boolean crafting) {
            inventory = fake(crafting ? CraftingInventory.class : PlayerInventory.class, (p, m, a) -> switch (m.getName()) {
                case "clear" -> { occupied = false; yield null; }
                case "setArmorContents" -> { check(a[0] == null, "Discard armour"); armour = false; yield null; }
                case "setItemInOffHand" -> { check(a[0] == null, "Discard the offhand item"); offHand = false; yield null; }
                default -> throw unexpected(m.getName());
            });
        }
    }

    private static final class PlayerState {
        final Store inventory = new Store();
        final Store top;
        Player player;
        final InventoryView view;
        boolean cursor = true, closed, activeCleared;
        Runnable onClose = () -> { };
        PlayerState(World world, boolean crafting) {
            UUID id = UUID.randomUUID();
            top = new Store(crafting);
            view = fake(InventoryView.class, (p, m, a) -> switch (m.getName()) {
                case "getTopInventory" -> top.inventory;
                case "getPlayer" -> player;
                default -> throw unexpected(m.getName());
            });
            player = fake(Player.class, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getInventory" -> inventory.inventory;
                case "getOpenInventory" -> view;
                case "clearActiveItem" -> { activeCleared = true; yield null; }
                case "setItemOnCursor" -> { check(a[0] == null, "Discard the cursor"); cursor = false; yield null; }
                case "closeInventory" -> { closed = true; onClose.run(); yield null; }
                case "sendMessage" -> null;
                default -> throw unexpected(m.getName());
            });
        }
    }

    private static final class StoredEntity {
        boolean removed, equipped = true;
        final Store store = new Store();
        final EntityEquipment equipment = fake(EntityEquipment.class, (p, m, a) -> {
            check(m.getName().equals("clear"), "Only clear entity equipment"); equipped = false; return null;
        });
        final Entity entity;
        StoredEntity(Class<?>... types) {
            entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), types, (p, m, a) -> switch (m.getName()) {
                case "remove" -> { removed = true; yield null; }
                case "getInventory" -> store.inventory;
                case "getEquipment" -> equipment;
                case "clearActiveItem" -> null;
                default -> throw unexpected(m.getName());
            });
        }
    }

    private static final class Arena {
        final List<ChunkState> chunks = new ArrayList<>();
        final World world = fake(World.class, (p, m, a) -> switch (m.getName()) {
            case "getLoadedChunks" -> chunks.stream().filter(c -> c.loaded).map(c -> c.chunk).toArray(Chunk[]::new);
            case "equals" -> p == a[0];
            default -> throw unexpected(m.getName());
        });
        ChunkState chunk(boolean loaded) {
            ChunkState state = new ChunkState(world, chunks.size(), loaded);
            chunks.add(state);
            return state;
        }
    }

    private static final class ChunkState {
        final List<BlockState> blocks = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final Chunk chunk;
        boolean loaded, entitiesLoaded;
        ChunkState(World world, long key, boolean loaded) {
            this.loaded = loaded;
            this.entitiesLoaded = loaded;
            chunk = fake(Chunk.class, (p, m, a) -> switch (m.getName()) {
                case "getWorld" -> world;
                case "getChunkKey" -> key;
                case "getTileEntities" -> blocks.toArray(BlockState[]::new);
                case "isEntitiesLoaded" -> entitiesLoaded;
                case "getEntities" -> {
                    check(entitiesLoaded, "Must not load entities to clean an inactive chunk");
                    yield entities.toArray(Entity[]::new);
                }
                default -> throw unexpected(m.getName());
            });
        }
    }

    private static <T> T fake(Class<? extends T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static AssertionError unexpected(String operation) {
        return new AssertionError("Unexpected operation: " + operation);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
