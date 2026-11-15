package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/** The lethal plane crosses solids; real water is painted only in already loaded match chunks. */
final class FloodModifier implements Listener, AutoCloseable {
    static final int START_BELOW = 20;
    static final int RISE_TICKS = 200;
    private static final int BLOCK_BUDGET = 2048;

    private static final class Fill {
        final Chunk chunk;
        final long key;
        int y;
        int index;
        int target;

        Fill(Chunk chunk, int y, int target) {
            this.chunk = chunk;
            this.key = key(chunk.getX(), chunk.getZ());
            this.y = y;
            this.target = target;
        }
    }

    private final World world;
    private final int feetY;
    private final int firstLayer;
    private final Map<Long, Integer> painted = new HashMap<>();
    private final Map<Long, Fill> pending = new HashMap<>();
    private final ArrayDeque<Fill> work = new ArrayDeque<>();
    private long ticks;
    private boolean closed;

    FloodModifier(World world, int feetY) {
        this.world = world;
        this.feetY = feetY;
        firstLayer = Math.max(world.getMinHeight(), surfaceAfterTicks(feetY, 0) - 1);
        enqueueLoaded();
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

    void tick() {
        if (closed) return;
        ticks++;
        if (ticks % RISE_TICKS == 0) enqueueLoaded();
        paint();
    }

    private void enqueueLoaded() {
        for (Chunk chunk : world.getLoadedChunks()) enqueue(chunk);
    }

    private void enqueue(Chunk chunk) {
        int top = Math.min(world.getMaxHeight() - 1, surfaceY() - 1);
        if (top < firstLayer) return;
        long key = key(chunk.getX(), chunk.getZ());
        Fill queued = pending.get(key);
        if (queued != null) {
            queued.target = top;
            return;
        }
        int from = painted.getOrDefault(key, firstLayer - 1) + 1;
        if (from > top) return;
        Fill fill = new Fill(chunk, from, top);
        pending.put(key, fill);
        work.addLast(fill);
    }

    /** Amortize large arena layers instead of placing every visible water block in one server tick. */
    private void paint() {
        int budget = BLOCK_BUDGET;
        while (budget > 0 && !work.isEmpty()) {
            Fill fill = work.getFirst();
            if (!world.isChunkLoaded(fill.chunk.getX(), fill.chunk.getZ())) {
                work.removeFirst();
                pending.remove(fill.key);
                continue;
            }
            while (budget > 0 && fill.y <= fill.target) {
                var block = fill.chunk.getBlock(fill.index & 15, fill.y, fill.index >> 4);
                if (block.getType().isAir()) block.setType(Material.WATER, false);
                budget--;
                if (++fill.index == 256) {
                    painted.put(fill.key, fill.y++);
                    fill.index = 0;
                }
            }
            if (fill.y > fill.target) {
                work.removeFirst();
                pending.remove(fill.key);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!closed && event.getWorld() == world) enqueue(event.getChunk());
    }

    /** Keep the flood flat: ordinary fluid flow would spill below its visual surface indefinitely. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!closed && event.getBlock().getWorld() == world && event.getBlock().getType() == Material.WATER
                && event.getBlock().getY() >= firstLayer && event.getBlock().getY() < surfaceY()) {
            event.setCancelled(true);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    @Override
    public void close() {
        closed = true;
        HandlerList.unregisterAll(this);
        pending.clear();
        work.clear();
        painted.clear();
        // The session owns and unloads this throwaway world; no template or lobby was modified.
    }
}
