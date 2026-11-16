import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Set;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Real disposable-world blocks and real session selection; only player transport is substituted. */
public final class FloodSmoke {
    private static final String PREFIX = "me.petr1furious.potatominigames.games.pillars.";
    private static final int FEET_Y = 100;
    private static final int INITIAL_SURFACE = 80;
    private final JavaPlugin smoke;
    private final ClassLoader loader;
    private final Server originalServer;
    private final Field serverField;
    private final Map<UUID, FakePlayer> transport = new LinkedHashMap<>();
    private final List<Object> alive = new ArrayList<>();
    private final Map<UUID, Object> participants = new LinkedHashMap<>();
    private World world;
    private Path worldFolder;
    private Object session;
    private Object flood;
    private Method tickFlood;
    private Method closeFlood;
    private Method tickModifier;
    private Method surfaceY;
    private Method prepareModifier;
    private Method isReady;
    private Method lastWorkUnits;
    private int activeTicks;
    private long deliveredCells;
    private long packetCalls;
    private int maxTickCells;
    private int maxWorkUnits;
    private long totalTickNanos;
    private long maxTickNanos;
    private int measuredTicks;
    private Server lookupServer;
    private BukkitTask callbackTask;
    private int assertions;

    private FloodSmoke(JavaPlugin smoke, JavaPlugin main) throws Exception {
        this.smoke = smoke;
        this.loader = main.getClass().getClassLoader();
        this.originalServer = Bukkit.getServer();
        this.serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
    }

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FloodSmoke(smoke, main).check();
    }

    private int check() throws Exception {
        require(Bukkit.isPrimaryThread(), "Flood smoke runs on the native server thread");
        try {
            createWorld();
            prepareSession();
            buildBarrierAndBox(0, 0);
            FakePlayer keeper = participant("keeper", 2.5, FEET_Y + 100, 2.5, true);
            FakePlayer submerged = participant("submerged", 6.5, INITIAL_SURFACE - .25, 6.5, true);
            FakePlayer barrier = participant("barrier", 4.5, INITIAL_SURFACE, 4.5, true);
            FakePlayer boxed = participant("boxed", 9.5, INITIAL_SURFACE, 9.5, true);
            FakePlayer stationary = participant("stationary", 12.5, INITIAL_SURFACE + 1, 12.5, true);
            FakePlayer beyondBounds = participant("beyond-render-bounds", 1600.5, INITIAL_SURFACE, 1600.5, true);
            FakePlayer alreadyDead = participant("already-dead", 3.5, INITIAL_SURFACE - 2, 3.5, true);
            alreadyDead.dead = true;
            Object disconnected = pillarPlayer(UUID.randomUUID());
            participants.put((UUID) get(disconnected, "id"), disconnected);
            alive.add(disconnected);
            FakePlayer spectator = participant("spectator", 7.5, INITIAL_SURFACE - 2, 7.5, false);
            spectator.gameMode = GameMode.SPECTATOR;
            FakePlayer outsider = player("nonparticipant", 5.5, INITIAL_SURFACE - 2, 5.5);
            prepareLookupServer();
            for (FakePlayer viewer : List.copyOf(transport.values())) sendChunk(viewer, 0, 0);
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    if (cx != 0 || cz != 0) sendChunk(keeper, cx, cz);
                }
            }
            sendChunk(keeper, 4, 0);
            require(!ready(), "The bounded initial and next surfaces require preparation, not a constructor burst");
            require(deliveredCells == 0, "Chunk notifications enqueue work rather than repainting synchronously");
            require(surface() == INITIAL_SURFACE, "The initial lethal surface is player feet minus twenty");
            long prepStart = System.nanoTime();
            int readyAtTick = -1;
            for (int i = 1; i <= 400; i++) {
                tickSession();
                if (readyAtTick < 0 && ready()) readyAtTick = i;
            }
            long prepNanos = System.nanoTime() - prepStart;
            require(ready(), "Countdown preparation completes both initial and next-layer caches and initial delivery");
            require((Integer) invoke(lastWorkUnits, flood) == 0,
                    "Ready countdowns have finished preparation work, not merely deferred it until the active clock");
            require(surface() == INITIAL_SURFACE && get(session, "started").equals(false),
                    "Two rise periods in the real countdown do not advance the active clock");
            require(totalDeaths() == 0 && transport.values().stream().allMatch(p -> p.lookups == 0),
                    "Countdown preparation does not select players for damage");
            require(client(keeper, 1, INITIAL_SURFACE - 1, 1) == Material.WATER
                            && client(spectator, 1, INITIAL_SURFACE - 1, 1) == Material.WATER
                            && client(outsider, 1, INITIAL_SURFACE - 1, 1) == Material.WATER,
                    "Players, spectators and nonparticipants see the initial water before release");
            require(waterChunks(keeper).size() == 49 && waterChunks(keeper).contains(key(-3, -3))
                            && waterChunks(keeper).contains(key(3, 3)) && !waterChunks(keeper).contains(key(4, 0)),
                    "The default visual footprint is exactly seven by seven chunks, even with a sent outside chunk");
            require(waterCount(keeper) <= 49 * 256 && onlyWaterY(keeper, INITIAL_SURFACE - 1),
                    "Only one bounded surface, never a submerged volume, is delivered initially");
            require(client(keeper, 4, INITIAL_SURFACE - 1, 4) != Material.WATER && boxIntact(0, 0),
                    "Client water preserves real solids and the sealed native box");
            assertNativeDry(keeper);
            long idlePackets = packetCalls;
            for (int i = 0; i < 30; i++) tickSession();
            require(packetCalls == idlePackets, "A ready countdown does not repeatedly repaint an idle surface");

            set(session, "started", true);
            tickSession();
            require(submerged.healthCalls == 1 && submerged.lastHealth == 0,
                    "An already-submerged live participant dies without moving");
            require(barrier.healthCalls == 1 && boxed.healthCalls == 1,
                    "A solid barrier or sealed dry box is no refuge from the lethal height");
            require(beyondBounds.healthCalls == 1,
                    "The lethal plane still applies to live players beyond the bounded rendered rectangle");
            require(!alive.contains(submerged.rosterEntry) && !alive.contains(barrier.rosterEntry)
                            && !alive.contains(boxed.rosterEntry),
                    "Synchronous death removal does not skip neighbors in the actual reverse roster traversal");
            require(block(9, INITIAL_SURFACE, 9) == Material.AIR && boxIntact(0, 0),
                    "Collision kills inside a dry native box without changing its real blocks");
            advanceTo(199);
            require(surface() == INITIAL_SURFACE && stationary.healthCalls == 0,
                    "A stationary player one block above the surface survives through active tick 199");
            require(keeper.healthCalls == 0 && alreadyDead.healthCalls == 0,
                    "Above-water and already-dead roster entries are not killed");
            assertExcluded(spectator, outsider);
            Location before = stationary.feet.clone();
            world.getBlockAt(1, INITIAL_SURFACE - 1, 1).setType(Material.LAPIS_BLOCK, false);
            tickSession();
            require(surface() == INITIAL_SURFACE + 1 && stationary.healthCalls == 1
                            && stationary.lastHealth == 0 && stationary.feet.equals(before),
                    "At exactly active tick 200 the rising plane kills a stationary player at equality");
            advanceTo(250);
            require(client(keeper, 1, INITIAL_SURFACE - 1, 1) == Material.LAPIS_BLOCK,
                    "A rise restores the previous fake plane to its CURRENT native block, not cached air");
            require(client(keeper, 1, INITIAL_SURFACE, 1) == Material.WATER
                            && onlyWaterY(keeper, INITIAL_SURFACE),
                    "The delivered water rises to the current plane and removes the old fake plane");
            assertExcluded(spectator, outsider);
            require(boxIntact(0, 0), "Rising client water retains every native box block");

            // Exercise real Paper packet events; native chunks and terrain remain genuine CraftBukkit objects.
            FakePlayer late = player("late-viewer", 34.5, FEET_Y + 100, 34.5);
            sendChunk(late, 2, 2);
            advanceTo(270);
            require(client(late, 33, INITIAL_SURFACE, 33) == Material.WATER
                            && !late.client.containsKey(Position.block(33, INITIAL_SURFACE - 1, 33)),
                    "Late viewers receive only the current surface, never historical submerged layers");
            unloadChunk(keeper, 2, 2);
            long unsentBefore = keeper.cells;
            advanceTo(280);
            require(keeper.cells == unsentBefore, "An unsent chunk does not trigger idle packet repaint");
            sendChunk(keeper, 2, 2);
            advanceTo(300);
            require(client(keeper, 33, INITIAL_SURFACE, 33) == Material.WATER
                            && onlyWaterY(keeper, INITIAL_SURFACE),
                    "A chunk unload/reload replays only the current surface");
            long lateBeforeLeave = late.cells;
            World lobby = originalServer.getWorlds().stream().filter(w -> w != world).findFirst().orElseThrow();
            late.feet.setWorld(lobby);
            advanceTo(320);
            require(late.cells == lateBeforeLeave, "Leaving the world abandons queued or idle client-water work");
            late.online = false;
            FakePlayer reconnect = new FakePlayer(late.id, "reconnected-viewer", new Location(world, 34.5, 200, 34.5));
            transport.put(reconnect.id, reconnect);
            sendChunk(reconnect, 2, 2);
            advanceTo(340);
            require(client(reconnect, 33, INITIAL_SURFACE, 33) == Material.WATER && late.cells == lateBeforeLeave,
                    "A new connection with the same UUID gets a fresh current surface without sending to the old connection");
            idlePackets = packetCalls;
            advanceTo(390);
            require(packetCalls == idlePackets, "Settled active ticks do not repaint an unchanged plane");

            // Extra native chunks are deliberately loaded outside the rectangle, never added to viewer work.
            for (int i = 0; i < 64; i++) {
                Chunk outside = loadFreshChunk(100 + i);
                sendChunk(keeper, outside.getX(), outside.getZ());
            }
            advanceTo(399);
            require(packetCalls == idlePackets,
                    "Loading and sending 64 out-of-bounds native chunks does not repaint or enlarge the current surface");
            long nativeBefore = nativeFingerprint(79, 106);
            long benchCells = deliveredCells;
            long benchPackets = packetCalls;
            long benchStart = System.nanoTime();
            int firstBenchTick = activeTicks;
            advanceTo(25 * 200 + 100);
            long benchNanos = System.nanoTime() - benchStart;
            require(surface() == INITIAL_SURFACE + 25, "Twenty-five consecutive rises retain the exact gameplay clock");
            require(nativeFingerprint(79, 106) == nativeBefore,
                    "Many consecutive rises, including loaded out-of-bounds chunks, do not mutate actual world blocks");
            require(waterChunks(keeper).size() == 49 && waterCount(keeper) <= 49 * 256
                            && onlyWaterY(keeper, surface() - 1),
                    "After many rises the rendered footprint and stored water remain one bounded current plane");
            require(maxTickCells <= 2048, "Every prepare/active callback delivers at most 2048 cell updates across all viewers");
            require(maxWorkUnits <= 2048, "Production preparation plus delivery work stays within its shared per-tick budget");
            assertNativeDry(keeper);
            smoke.getLogger().info("PILLARS_FLOOD_COST: countdownPrepareMs=" + prepNanos / 1_000_000.0
                    + " firstReadyCountdownTick=" + readyAtTick
                    + " benchmarkTicks=" + (activeTicks - firstBenchTick) + " consecutiveRises=24"
                    + " loadedChunks=" + world.getLoadedChunks().length + " renderedChunks=" + waterChunks(keeper).size()
                    + " benchmarkMs=" + benchNanos / 1_000_000.0 + " deliveredCells=" + (deliveredCells - benchCells)
                    + " packets=" + (packetCalls - benchPackets) + " maxCellsPerTick=" + maxTickCells
                    + " maxWorkUnitsPerTick=" + maxWorkUnits + " measuredTicks=" + measuredTicks
                    + " meanCallbackMicros=" + totalTickNanos / (double) measuredTicks / 1_000.0
                    + " maxCallbackMicros=" + maxTickNanos / 1_000.0);

            int currentY = surface() - 1;
            world.getBlockAt(1, currentY, 1).setType(Material.EMERALD_BLOCK, false);
            unloadChunk(keeper, 3, 3);
            FakePlayer queued = player("queued-at-close", 18.5, 200, 18.5);
            sendChunk(queued, 1, 1);
            callbackTask = Bukkit.getScheduler().runTaskTimer(smoke, () -> {
                try { tickSession(); }
                catch (Exception failure) { throw new IllegalStateException("Flood smoke callback", failure); }
            }, 1L, 1L);
            set(session, "floodTask", callbackTask);
            require(Bukkit.getScheduler().isQueued(callbackTask.getTaskId()), "Cleanup starts with a real scheduled callback");
            invoke(closeFlood, session);
            require(callbackTask.isCancelled() && !Bukkit.getScheduler().isQueued(callbackTask.getTaskId())
                            && get(session, "floodTask") == null,
                    "Production closeFlood cancels and releases the actual gameplay callback");
            require(client(keeper, 1, currentY, 1) == Material.EMERALD_BLOCK,
                    "Close restores a current real block changed underneath the fake water");
            require(transport.values().stream().filter(p -> p.online && p.feet.getWorld() == world)
                            .allMatch(p -> waterCount(p) == 0),
                    "Close removes every still-sent fake water cell for connected world viewers");
            require(registeredFloodListeners() == 0, "Close unregisters every native Flood packet listener");
            int closedSurface = surface();
            int deathsBeforeClose = totalDeaths();
            long closedPackets = packetCalls;
            sendChunk(queued, 0, 1);
            for (int i = 0; i < 250; i++) {
                invoke(tickModifier, flood);
                invoke(prepareModifier, flood);
            }
            require(surface() == closedSurface && packetCalls == closedPackets && queued.client.isEmpty(),
                    "Closed prepare/tick and subsequent chunk events do not advance time or drain abandoned queues");
            set(session, "ending", true);
            keeper.feet.setY(INITIAL_SURFACE - 2);
            for (int i = 0; i < 250; i++) tickSession();
            require(totalDeaths() == deathsBeforeClose && keeper.healthCalls == 0 && surface() == closedSurface,
                    "Ended-session callbacks cannot select newly submerged survivors or restart Flood");
            invoke(closeFlood, session);
            require(callbackTask.isCancelled() && registeredFloodListeners() == 0,
                    "Repeated cleanup leaves callbacks cancelled and listeners removed");
            checkExpandedFootprint();
            smoke.getLogger().info("PILLARS_FLOOD_SMOKE_PASS: " + assertions + " assertions");
            return assertions;
        } finally {
            serverField.set(null, originalServer);
            try {
                if (callbackTask != null) callbackTask.cancel();
                if (flood != null) ((AutoCloseable) flood).close();
            } finally {
                serverField.set(null, originalServer);
                if (world != null) {
                    if (!Bukkit.unloadWorld(world, false)) {
                        throw new IllegalStateException("Could not unload disposable Flood smoke world " + world.getName());
                    }
                    deleteWorldFolder();
                }
            }
        }
    }

    private void createWorld() throws Exception {
        String name = "pillars_flood_smoke_" + UUID.randomUUID().toString().replace("-", "");
        world = Bukkit.createWorld(new WorldCreator(name).generateStructures(false)
                .keepSpawnLoaded(TriState.FALSE).generator(new ChunkGenerator() {
                    @Override public Location getFixedSpawnLocation(World world, Random random) {
                        return new Location(world, .5, FEET_Y, .5);
                    }
                }));
        require(world != null, "A dedicated disposable native void world is created");
        worldFolder = world.getWorldFolder().toPath();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) world.getChunkAt(x, z);
        }
        // Warm native block access while Bukkit still contains the genuine CraftServer.
        world.getBlockAt(1, INITIAL_SURFACE - 1, 1).setType(Material.AIR, false);
    }

    private void prepareSession() throws Exception {
        // Resolve/initialize production types with the plugin's loader before temporarily changing Bukkit.
        Class<?> sessionType = Class.forName(PREFIX + "PillarsSession", true, loader);
        Class<?> floodType = Class.forName(PREFIX + "FloodModifier", true, loader);
        Class.forName(PREFIX + "PillarsSession$PillarPlayer", true, loader);
        session = bare(sessionType);
        set(session, "alive", alive);
        set(session, "players", participants);
        set(session, "world", world);
        set(session, "floodPosition", new Location(world, 0, 0, 0));
        set(session, "startCountdown", 5L);
        set(session, "started", false);
        set(session, "ending", false);
        Constructor<?> constructor = floodType.getDeclaredConstructor(World.class, int.class, Location.class, List.class);
        constructor.setAccessible(true);
        flood = constructor.newInstance(world, FEET_Y, new Location(world, .5, FEET_Y, .5),
                List.of(new Location(world, -32, FEET_Y, -32), new Location(world, 32, FEET_Y, 32)));
        set(session, "flood", flood);
        tickFlood = method(sessionType, "tickFlood");
        closeFlood = method(sessionType, "closeFlood");
        tickModifier = method(floodType, "tick");
        surfaceY = method(floodType, "surfaceY");
        prepareModifier = method(floodType, "prepare");
        isReady = method(floodType, "isReady");
        lastWorkUnits = method(floodType, "lastWorkUnits");
        Bukkit.getPluginManager().registerEvents((Listener) flood, smoke);
    }

    private void buildBarrierAndBox(int x, int z) {
        world.getBlockAt(x + 4, INITIAL_SURFACE - 1, z + 4).setType(Material.STONE, false);
        for (int dx = 8; dx <= 10; dx++) {
            for (int dz = 8; dz <= 10; dz++) {
                for (int y = INITIAL_SURFACE - 1; y <= INITIAL_SURFACE + 3; y++) {
                    boolean shell = dx == 8 || dx == 10 || dz == 8 || dz == 10
                            || y == INITIAL_SURFACE - 1 || y == INITIAL_SURFACE + 3;
                    world.getBlockAt(x + dx, y, z + dz).setType(shell ? Material.STONE : Material.AIR, false);
                }
            }
        }
    }

    private boolean boxIntact(int x, int z) {
        for (int dx = 8; dx <= 10; dx++) {
            for (int dz = 8; dz <= 10; dz++) {
                for (int y = INITIAL_SURFACE - 1; y <= INITIAL_SURFACE + 3; y++) {
                    if ((dx == 8 || dx == 10 || dz == 8 || dz == 10
                            || y == INITIAL_SURFACE - 1 || y == INITIAL_SURFACE + 3)
                            && block(x + dx, y, z + dz) != Material.STONE) return false;
                }
            }
        }
        return true;
    }

    private FakePlayer participant(String name, double x, double y, double z, boolean live) throws Exception {
        FakePlayer player = player(name, x, y, z);
        player.rosterEntry = pillarPlayer(player.id);
        participants.put(player.id, player.rosterEntry);
        if (live) alive.add(player.rosterEntry);
        return player;
    }

    private Object pillarPlayer(UUID id) throws Exception {
        Object entry = bare(Class.forName(PREFIX + "PillarsSession$PillarPlayer", true, loader));
        set(entry, "id", id);
        return entry;
    }

    private FakePlayer player(String name, double x, double y, double z) {
        FakePlayer player = new FakePlayer(name, new Location(world, x, y, z));
        transport.put(player.id, player);
        return player;
    }

    private void prepareLookupServer() {
        lookupServer = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] {Server.class},
                (receiver, operation, args) -> {
                    if (operation.getName().equals("getPlayer") && args != null && args.length == 1
                            && args[0] instanceof UUID id) {
                        FakePlayer player = transport.get(id);
                        if (player != null) player.lookups++;
                        return player == null || !player.online ? null : player.proxy;
                    }
                    if (operation.getName().equals("getOnlinePlayers")) {
                        return transport.values().stream().map(p -> p.proxy).toList();
                    }
                    // Native factories/remapping require CraftServer, not a dynamic Server proxy.
                    serverField.set(null, originalServer);
                    try { return operation.invoke(originalServer, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                    finally { serverField.set(null, receiver); }
                });
    }

    private void tickSession() throws Exception {
        Server previous = Bukkit.getServer();
        long cellsBefore = deliveredCells;
        long begin = System.nanoTime();
        boolean active = (Boolean) get(session, "started") && !(Boolean) get(session, "ending");
        serverField.set(null, lookupServer);
        try { invoke(tickFlood, session); }
        finally { serverField.set(null, previous); }
        if (active) activeTicks++;
        recordWork(cellsBefore, begin);
    }

    private void recordWork(long cellsBefore, long begin) throws Exception {
        long elapsed = System.nanoTime() - begin;
        int cells = Math.toIntExact(deliveredCells - cellsBefore);
        int work = (Integer) invoke(lastWorkUnits, flood);
        maxTickCells = Math.max(maxTickCells, cells);
        maxWorkUnits = Math.max(maxWorkUnits, work);
        totalTickNanos += elapsed;
        maxTickNanos = Math.max(maxTickNanos, elapsed);
        measuredTicks++;
        require(cells <= 2048 && work <= 2048,
                "Each native callback respects the shared preparation/delivery cell budget");
    }

    private void advanceTo(int target) throws Exception {
        while (activeTicks < target) tickSession();
    }

    private boolean ready() throws Exception { return (Boolean) invoke(isReady, flood); }

    private void sendChunk(FakePlayer viewer, int x, int z) throws Exception {
        Chunk chunk = world.getChunkAt(x, z);
        viewer.sentChunks.add(chunk.getChunkKey());
        packetEvent("PlayerChunkLoadEvent", viewer, chunk);
    }

    private void unloadChunk(FakePlayer viewer, int x, int z) throws Exception {
        Chunk chunk = world.getChunkAt(x, z);
        viewer.sentChunks.remove(chunk.getChunkKey());
        viewer.client.keySet().removeIf(p -> p.blockX() >> 4 == x && p.blockZ() >> 4 == z);
        packetEvent("PlayerChunkUnloadEvent", viewer, chunk);
    }

    private void packetEvent(String name, FakePlayer viewer, Chunk chunk) throws Exception {
        Class<?> type = Class.forName("io.papermc.paper.event.packet." + name);
        Constructor<?> constructor = type.getConstructor(Chunk.class, Player.class);
        Bukkit.getPluginManager().callEvent((Event) constructor.newInstance(chunk, viewer.proxy));
    }

    private static long key(int x, int z) {
        return (x & 0xffffffffL) | ((long) z << 32);
    }

    private Material client(FakePlayer viewer, int x, int y, int z) {
        BlockData data = viewer.client.get(Position.block(x, y, z));
        return data == null ? null : data.getMaterial();
    }

    private int waterCount(FakePlayer viewer) {
        return (int) viewer.client.values().stream().filter(d -> d.getMaterial() == Material.WATER).count();
    }

    private Set<Long> waterChunks(FakePlayer viewer) {
        Set<Long> chunks = new HashSet<>();
        viewer.client.forEach((position, data) -> {
            if (data.getMaterial() == Material.WATER) chunks.add(key(position.blockX() >> 4, position.blockZ() >> 4));
        });
        return chunks;
    }

    private boolean onlyWaterY(FakePlayer viewer, int y) {
        return viewer.client.entrySet().stream().allMatch(e -> e.getValue().getMaterial() != Material.WATER
                || e.getKey().blockY() == y);
    }

    private void assertNativeDry(FakePlayer viewer) {
        boolean dry = viewer.client.entrySet().stream().filter(e -> e.getValue().getMaterial() == Material.WATER)
                .allMatch(e -> block(e.getKey().blockX(), e.getKey().blockY(), e.getKey().blockZ()).isAir());
        require(dry, "Every delivered water cell remains native dry air; Flood does not create fluid/light world work");
    }

    private long nativeFingerprint(int minY, int maxY) {
        List<Chunk> chunks = new ArrayList<>(List.of(world.getLoadedChunks()));
        chunks.sort(Comparator.comparingInt(Chunk::getX).thenComparingInt(Chunk::getZ));
        long fingerprint = 1;
        for (Chunk chunk : chunks) {
            fingerprint = fingerprint * 31 + chunk.getChunkKey();
            for (int y = minY; y <= maxY; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        fingerprint = fingerprint * 31 + chunk.getBlock(x, y, z).getBlockData().getAsString().hashCode();
                    }
                }
            }
        }
        return fingerprint;
    }

    private void checkExpandedFootprint() throws Exception {
        // A second real renderer proves large layouts are expanded, not silently clipped to 5x5 pillar chunks.
        transport.clear();
        FakePlayer viewer = player("expanded-layout", .5, 200, .5);
        Constructor<?> constructor = flood.getClass().getDeclaredConstructor(World.class, int.class, Location.class, List.class);
        constructor.setAccessible(true);
        flood = constructor.newInstance(world, FEET_Y, new Location(world, .5, FEET_Y, .5),
                List.of(new Location(world, -5 * 16 + .5, FEET_Y, 4 * 16 + .5)));
        Bukkit.getPluginManager().registerEvents((Listener) flood, smoke);
        for (int x = -7; x <= 4; x++) {
            for (int z = -4; z <= 6; z++) sendChunk(viewer, x, z);
        }
        long begin = System.nanoTime();
        long cellsBefore = deliveredCells;
        for (int i = 0; i < 400; i++) {
            long tickBegin = System.nanoTime();
            long tickCells = deliveredCells;
            invoke(prepareModifier, flood);
            recordWork(tickCells, tickBegin);
        }
        require(ready() && surface() == INITIAL_SURFACE,
                "Expanded initial and next surfaces prepare fully without advancing the active clock");
        require(waterChunks(viewer).size() == 90 && waterChunks(viewer).contains(key(-6, 5))
                        && waterChunks(viewer).contains(key(3, -3)),
                "An outlying pillar expands only the required edges, including its one-chunk margin");
        require(!waterChunks(viewer).contains(key(-7, 5)) && !waterChunks(viewer).contains(key(-6, 6))
                        && !waterChunks(viewer).contains(key(4, 0)),
                "Even the expanded footprint excludes sent chunks beyond its exact bounded rectangle");
        require(onlyWaterY(viewer, INITIAL_SURFACE - 1) && waterCount(viewer) <= 90 * 256,
                "Expanded layouts still send one bounded surface rather than a submerged volume");
        assertNativeDry(viewer);
        smoke.getLogger().info("PILLARS_FLOOD_EXPANDED_COST: prepareTicks=400 renderedChunks="
                + waterChunks(viewer).size() + " deliveredCells=" + (deliveredCells - cellsBefore)
                + " preparationMs=" + (System.nanoTime() - begin) / 1_000_000.0);
        ((AutoCloseable) flood).close();
        require(waterCount(viewer) == 0 && registeredFloodListeners() == 0,
                "Expanded renderer cleanup restores the surface and unregisters packet listeners");
    }

    private Chunk loadFreshChunk(int start) {
        int x = start;
        while (world.isChunkLoaded(x, start)) x++;
        return world.getChunkAt(x, start);
    }

    private int surface() throws Exception { return (Integer) invoke(surfaceY, flood); }
    private Material block(int x, int y, int z) { return world.getBlockAt(x, y, z).getType(); }
    private int totalDeaths() { return transport.values().stream().mapToInt(p -> p.healthCalls).sum(); }
    private int registeredFloodListeners() {
        int count = 0;
        for (var listener : HandlerList.getRegisteredListeners(smoke)) {
            if (listener.getListener() == flood) count++;
        }
        return count;
    }

    private void assertExcluded(FakePlayer spectator, FakePlayer outsider) {
        require(spectator.healthCalls == 0 && outsider.healthCalls == 0
                        && spectator.lookups == 0 && outsider.lookups == 0,
                "Online spectators and nonparticipants below the plane are not selected from outside the live roster");
    }

    private void deleteWorldFolder() throws Exception {
        // Only this fixture's successfully created, UUID-named world is ever removed.
        if (worldFolder == null) return;
        try (var files = Files.walk(worldFolder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private final class FakePlayer {
        final UUID id;
        final String name;
        final Location feet;
        final Player proxy;
        final Set<Long> sentChunks = new HashSet<>();
        final Map<BlockPosition, BlockData> client = new LinkedHashMap<>();
        long cells;
        boolean online = true;
        Object rosterEntry;
        boolean dead;
        GameMode gameMode = GameMode.SURVIVAL;
        int healthCalls;
        int lookups;
        double lastHealth = Double.NaN;

        FakePlayer(String name, Location feet) {
            this(UUID.randomUUID(), name, feet);
        }

        FakePlayer(UUID id, String name, Location feet) {
            this.id = id;
            this.name = name;
            this.feet = feet;
            proxy = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (receiver, operation, args) -> switch (operation.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "getWorld" -> feet.getWorld();
                        case "getLocation" -> {
                            if (args == null || args.length == 0) yield feet.clone();
                            Location target = (Location) args[0];
                            if (target == null) yield null;
                            target.setWorld(feet.getWorld());
                            target.setX(feet.getX());
                            target.setY(feet.getY());
                            target.setZ(feet.getZ());
                            target.setYaw(feet.getYaw());
                            target.setPitch(feet.getPitch());
                            yield target;
                        }
                        case "getGameMode" -> gameMode;
                        case "isDead" -> dead;
                        case "isOnline" -> online;
                        case "getSentChunkKeys" -> {
                            if (operation.getReturnType().isInstance(sentChunks)) yield Set.copyOf(sentChunks);
                            // Some Paper releases expose the native fastutil LongSet rather than Set<Long>.
                            yield Class.forName("it.unimi.dsi.fastutil.longs.LongOpenHashSet")
                                    .getConstructor(java.util.Collection.class).newInstance(sentChunks);
                        }
                        case "isChunkSent" -> {
                            if (args.length == 1 && args[0] instanceof Number value) {
                                yield sentChunks.contains(value.longValue());
                            }
                            if (args.length == 1 && args[0] instanceof Chunk chunk) {
                                yield sentChunks.contains(chunk.getChunkKey());
                            }
                            yield sentChunks.contains(key(((Number) args[0]).intValue(), ((Number) args[1]).intValue()));
                        }
                        case "sendMultiBlockChange" -> {
                            if (!online || feet.getWorld() != world) {
                                throw new AssertionError("Flood sends water to an offline or departed connection");
                            }
                            Map<?, ?> changes = (Map<?, ?>) args[0];
                            packetCalls++;
                            for (var change : changes.entrySet()) {
                                BlockPosition position = ((Position) change.getKey()).toBlock();
                                if (!sentChunks.contains(key(position.blockX() >> 4, position.blockZ() >> 4))) {
                                    throw new AssertionError("Flood sends a block in a chunk not sent to this viewer");
                                }
                                client.put(position, ((BlockData) change.getValue()).clone());
                                cells++;
                                deliveredCells++;
                            }
                            yield null;
                        }
                        case "getHealth" -> dead ? 0.0 : 20.0;
                        case "setHealth" -> {
                            healthCalls++;
                            lastHealth = ((Number) args[0]).doubleValue();
                            if (lastHealth != 0.0) throw new AssertionError("Unexpected nonlethal Flood health change");
                            dead = true;
                            // Bukkit death delivery removes an entry synchronously. Only this transport effect
                            // is supplied here; the production session chooses the victim and sets lethal health.
                            alive.remove(rosterEntry);
                            yield null;
                        }
                        case "equals" -> receiver == args[0];
                        case "hashCode" -> id.hashCode();
                        case "toString" -> "FloodSmokePlayer[" + name + "]";
                        default -> throw new AssertionError("Unexpected player transport call: " + operation.getName());
                    });
        }
    }

    private static Object bare(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }
    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
