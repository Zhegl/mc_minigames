package me.petr1furious.potatominigames.games.pillars;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Client-only water surface; the session applies the lethal plane independently of these visuals. */
final class FloodModifier implements Listener, AutoCloseable {
    static final int START_BELOW = 20;
    static final int RISE_TICKS = 200;
    private static final int BLOCK_BUDGET = 2048;
    private static final int CACHE_BUDGET = 512;
    private static final int CHUNK_CELLS = 256;

    private static final class Column {
        final int x;
        final int z;
        final long key;
        int index;

        Column(int x, int z) {
            this.x = x;
            this.z = z;
            key = key(x, z);
        }
    }
    private record Shown(int y, BitSet cells) {}

    private static final class PreparedChunk {
        final BlockPosition[] positions = new BlockPosition[CHUNK_CELLS];
        final Map<BlockPosition, BlockData> water = new HashMap<>(CHUNK_CELLS * 2);
        final BitSet cells = new BitSet(CHUNK_CELLS);
        long refreshedPass;
    }

    private final class Plane {
        final int surface;
        final int y;
        final boolean renderable;
        final PreparedChunk[] chunks = new PreparedChunk[columns.size()];
        final BitSet pending = new BitSet(columns.size());

        Plane(int surface) {
            this.surface = surface;
            y = surface - 1;
            renderable = surface > world.getMinHeight() && surface <= world.getMaxHeight();
            if (renderable) pending.set(0, columns.size());
        }
    }

    private static final class View {
        final Player player;
        final Location position;
        final Set<Column> visible = new LinkedHashSet<>();
        final Set<Column> pending = new LinkedHashSet<>();
        final Map<Long, Shown> shown = new HashMap<>();
        boolean active = true;
        boolean queued;

        View(Player player, World world) {
            this.player = player;
            position = new Location(world, 0, 0, 0);
        }
    }

    private final World world;
    private final int feetY;
    private final BlockData water = Bukkit.createBlockData(Material.WATER);
    private final List<Column> columns;
    private final Map<Long, Column> byKey = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();
    private final ArrayDeque<View> delivery = new ArrayDeque<>();
    private Plane current;
    private Plane next;
    private long ticks;
    private long workPass;
    private int lastWorkUnits;
    private boolean ready;
    private boolean closed;

    FloodModifier(World world, int feetY, Location center, List<Location> pillars) {
        this.world = world;
        this.feetY = feetY;
        int centerX = center.getBlockX() >> 4;
        int centerZ = center.getBlockZ() >> 4;
        int minX = centerX - 3;
        int maxX = centerX + 3;
        int minZ = centerZ - 3;
        int maxZ = centerZ + 3;
        for (Location pillar : pillars) {
            int x = pillar.getBlockX() >> 4;
            int z = pillar.getBlockZ() >> 4;
            minX = Math.min(minX, x - 1);
            maxX = Math.max(maxX, x + 1);
            minZ = Math.min(minZ, z - 1);
            maxZ = Math.max(maxZ, z + 1);
        }
        columns = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) columns.add(new Column(x, z));
        columns.sort(Comparator.comparingLong(column -> distance(column.x, column.z, centerX, centerZ)));
        for (int index = 0; index < columns.size(); index++) {
            Column column = columns.get(index);
            column.index = index;
            byKey.put(column.key, column);
        }
        current = new Plane(surfaceAfterTicks(feetY, 0));
        next = new Plane(surfaceAfterTicks(feetY, RISE_TICKS));
    }

    static int surfaceAfterTicks(int feetY, long ticks) {
        return (int) Math.min(Integer.MAX_VALUE, (long) feetY - START_BELOW + ticks / RISE_TICKS);
    }

    static boolean touches(double playerFeetY, int surfaceY) {
        return playerFeetY <= surfaceY;
    }

    int surfaceY() {
        return surfaceAfterTicks(feetY, ticks);
    }

    /** Cache and deliver the initial surface without spending any active-game time. */
    void prepare() {
        if (!closed) work();
    }

    boolean isReady() {
        return !closed && ready;
    }

    /** Conservative terrain-read plus packet-cell work charged by the last prepare/tick. */
    int lastWorkUnits() {
        return lastWorkUnits;
    }

    void tick() {
        if (closed) return;
        ticks++;
        int surface = surfaceY();
        if (surface != current.surface) {
            current = next.surface == surface ? next : new Plane(surface);
            next = new Plane(surface == Integer.MAX_VALUE ? surface : surface + 1);
            for (View view : views.values()) {
                // A slow connection skips straight to the newest surface, never queues old layers.
                view.pending.addAll(view.visible);
                enqueue(view);
            }
        }
        work();
    }

    private void work() {
        lastWorkUnits = 0;
        workPass++;
        discoverViewers();
        // Reserve background capacity so preparing the next rise cannot be starved by spectators.
        lastWorkUnits += prepareCaches(CACHE_BUDGET);
        int stalled = 0;
        while (!delivery.isEmpty() && stalled < delivery.size()) {
            View view = delivery.removeFirst();
            view.queued = false;
            if (!view.active || !available(view.player)) continue;
            Column column = nearestPending(view);
            if (column == null) continue;
            int remaining = BLOCK_BUDGET - lastWorkUnits;
            PreparedChunk prepared = current.chunks[column.index];
            if (current.renderable && prepared == null) {
                if (remaining < CHUNK_CELLS) {
                    enqueue(view);
                    stalled++;
                    continue;
                }
                prepared = prepareChunk(current, column);
                lastWorkUnits += CHUNK_CELLS;
                remaining -= CHUNK_CELLS;
            }
            Shown previous = view.shown.get(column.key);
            int cost = (current.renderable ? CHUNK_CELLS * 2 : 0)
                    + (previous == null ? 0 : previous.cells.cardinality() * 2);
            if (remaining < cost) {
                enqueue(view);
                stalled++;
                continue;
            }
            // A chunk replacement is one work item: restore the old surface before showing the new.
            if (previous != null) restore(view, column, previous);
            view.shown.remove(column.key);
            if (prepared != null) {
                refresh(prepared);
                if (!prepared.water.isEmpty()) {
                    view.player.sendMultiBlockChange(prepared.water);
                    view.shown.put(column.key, new Shown(current.y, (BitSet) prepared.cells.clone()));
                }
            }
            lastWorkUnits += cost;
            view.pending.remove(column);
            enqueue(view);
            stalled = 0;
        }
        // Spend any unusable delivery remainder on cache work, still under the same global budget.
        lastWorkUnits += prepareCaches(BLOCK_BUDGET - lastWorkUnits);
        // Readiness gates GO only; preparing later rises must not revoke initial readiness.
        if (ticks != 0) return;
        ready = current.pending.isEmpty() && next.pending.isEmpty();
        if (ready)
            for (View view : views.values())
                if (!view.pending.isEmpty()) {
                    ready = false;
                    break;
                }
    }

    private int prepareCaches(int budget) {
        int used = preparePlane(current, budget);
        return used + preparePlane(next, budget - used);
    }

    private int preparePlane(Plane plane, int budget) {
        int used = 0;
        for (int index = plane.pending.nextSetBit(0); index >= 0;
                index = plane.pending.nextSetBit(index + 1)) {
            Column column = columns.get(index);
            if (!world.isChunkLoaded(column.x, column.z)) {
                // No chunk loads/generation, and no wait for chunks that no client can see yet.
                plane.pending.clear(index);
                continue;
            }
            if (budget - used < CHUNK_CELLS) break;
            prepareChunk(plane, column);
            used += CHUNK_CELLS;
        }
        return used;
    }

    private PreparedChunk prepareChunk(Plane plane, Column column) {
        PreparedChunk prepared = new PreparedChunk();
        for (int index = 0; index < CHUNK_CELLS; index++)
            prepared.positions[index] = Position.block(
                    (column.x << 4) + (index & 15), plane.y, (column.z << 4) + (index >> 4));
        refresh(prepared);
        plane.chunks[column.index] = prepared;
        plane.pending.clear(column.index);
        return prepared;
    }

    /** A precomputed air snapshot must never disguise a solid placed before this packet is sent. */
    private void refresh(PreparedChunk prepared) {
        // One synchronous pass can safely share this terrain snapshot among every viewer.
        if (prepared.refreshedPass == workPass) return;
        prepared.refreshedPass = workPass;
        for (int index = 0; index < CHUNK_CELLS; index++) {
            BlockPosition position = prepared.positions[index];
            if (world.getBlockAt(position.blockX(), position.blockY(), position.blockZ()).getType().isAir()) {
                prepared.water.put(position, water);
                prepared.cells.set(index);
            } else {
                prepared.water.remove(position);
                prepared.cells.clear(index);
            }
        }
    }

    private void discoverViewers() {
        for (Player player : world.getPlayers())
            if (available(player)) view(player);
        for (Iterator<View> iterator = views.values().iterator(); iterator.hasNext();) {
            View view = iterator.next();
            if (!available(view.player)) {
                discard(view);
                iterator.remove();
            }
        }
    }

    private boolean available(Player player) {
        return player.isOnline() && player.getWorld() == world;
    }

    private View view(Player player) {
        UUID id = player.getUniqueId();
        View view = views.get(id);
        if (view != null && view.player == player) return view;
        if (view != null) discard(view);
        view = new View(player, world);
        views.put(id, view);
        for (Column column : columns)
            if (player.isChunkSent(column.key)) request(view, column);
        return view;
    }

    private void request(View view, Column column) {
        view.visible.add(column);
        view.pending.add(column);
        if (current.renderable && current.chunks[column.index] == null) current.pending.set(column.index);
        if (next.renderable && next.chunks[column.index] == null) next.pending.set(column.index);
        if (ticks == 0) ready = false;
        enqueue(view);
    }

    private void enqueue(View view) {
        if (view.active && !view.queued && !view.pending.isEmpty()) {
            view.queued = true;
            delivery.addLast(view);
        }
    }

    private Column nearestPending(View view) {
        view.player.getLocation(view.position);
        int x = view.position.getBlockX() >> 4;
        int z = view.position.getBlockZ() >> 4;
        Column nearest = null;
        long best = Long.MAX_VALUE;
        for (Iterator<Column> iterator = view.pending.iterator(); iterator.hasNext();) {
            Column column = iterator.next();
            if (!view.player.isChunkSent(column.key) || !world.isChunkLoaded(column.x, column.z)) {
                iterator.remove();
                view.visible.remove(column);
                view.shown.remove(column.key);
                continue;
            }
            long distance = distance(column.x, column.z, x, z);
            if (distance < best) {
                nearest = column;
                best = distance;
            }
        }
        return nearest;
    }

    private void restore(View view, Column column, Shown shown) {
        Map<BlockPosition, BlockData> updates = new HashMap<>(shown.cells.cardinality() * 2);
        for (int index = shown.cells.nextSetBit(0); index >= 0;
                index = shown.cells.nextSetBit(index + 1)) {
            int x = (column.x << 4) + (index & 15);
            int z = (column.z << 4) + (index >> 4);
            updates.put(Position.block(x, shown.y, z), world.getBlockAt(x, shown.y, z).getBlockData());
        }
        if (!updates.isEmpty()) view.player.sendMultiBlockChange(updates);
    }

    @EventHandler
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        if (closed || event.getWorld() != world || !available(player)) return;
        Column column = byKey.get(key(event.getChunk().getX(), event.getChunk().getZ()));
        if (column == null) return;
        View view = view(player);
        // A resent real chunk has already replaced the previous client-only surface.
        view.shown.remove(column.key);
        request(view, column);
    }

    @EventHandler
    public void onChunkUnload(PlayerChunkUnloadEvent event) {
        View view = views.get(event.getPlayer().getUniqueId());
        if (closed || event.getWorld() != world || view == null || view.player != event.getPlayer()) return;
        Column column = byKey.get(key(event.getChunk().getX(), event.getChunk().getZ()));
        if (column == null) return;
        view.visible.remove(column);
        view.pending.remove(column);
        view.shown.remove(column.key);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        release(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (event.getPlayer().getWorld() != world) release(event.getPlayer());
    }

    private void release(Player player) {
        View view = views.get(player.getUniqueId());
        if (view != null && view.player == player) {
            views.remove(player.getUniqueId());
            discard(view);
        }
    }

    private void discard(View view) {
        view.active = false;
        view.visible.clear();
        view.pending.clear();
        view.shown.clear();
    }

    private static long distance(int x, int z, int toX, int toZ) {
        long dx = (long) x - toX;
        long dz = (long) z - toZ;
        return dx * dx + dz * dz;
    }

    private static long key(int x, int z) {
        // Paper's sent-chunk keys use Minecraft's x-low/z-high ChunkPos encoding.
        return (x & 0xffffffffL) | ((long) z << 32);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(this);
        for (View view : views.values()) {
            if (!available(view.player)) continue;
            for (Map.Entry<Long, Shown> entry : view.shown.entrySet()) {
                Column column = byKey.get(entry.getKey());
                if (view.player.isChunkSent(column.key) && world.isChunkLoaded(column.x, column.z))
                    restore(view, column, entry.getValue());
            }
            discard(view);
        }
        views.clear();
        delivery.clear();
        current = null;
        next = null;
        ready = false;
    }
}
