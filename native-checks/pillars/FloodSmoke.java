import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
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
            int x = 0, z = 0;
            buildBarrierAndBox(x, z);
            FakePlayer keeper = participant("keeper", x + 2.5, FEET_Y, z + 2.5, true);
            FakePlayer submerged = participant("submerged", x + 6.5, INITIAL_SURFACE - .25, z + 6.5, true);
            FakePlayer barrier = participant("barrier", x + 4.5, INITIAL_SURFACE, z + 4.5, true);
            FakePlayer boxed = participant("boxed", x + 9.5, INITIAL_SURFACE, z + 9.5, true);
            FakePlayer stationary = participant("stationary", x + 12.5, INITIAL_SURFACE + 1, z + 12.5, true);
            FakePlayer alreadyDead = participant("already-dead", x + 3.5, INITIAL_SURFACE - 2, z + 3.5, true);
            alreadyDead.dead = true;
            Object disconnected = pillarPlayer(UUID.randomUUID());
            participants.put((UUID) get(disconnected, "id"), disconnected);
            alive.add(disconnected);
            FakePlayer spectator = participant("spectator", x + 7.5, INITIAL_SURFACE - 2, z + 7.5, false);
            spectator.gameMode = GameMode.SPECTATOR;
            FakePlayer outsider = player("nonparticipant", x + 5.5, INITIAL_SURFACE - 2, z + 5.5);
            prepareLookupServer();

            // The first loaded chunk is first in the production render queue. Its probe avoids the box.
            Chunk first = world.getLoadedChunks()[0];
            int probeX = first.getX() * 16 + 1, probeZ = first.getZ() * 16 + 1;
            require(block(probeX, INITIAL_SURFACE - 1, probeZ) == Material.AIR,
                    "The isolated void world's initial water probe starts as air");
            require(surface() == INITIAL_SURFACE,
                    "The initial lethal/water surface is player feet minus twenty, not platform block height");
            require(get(session, "started").equals(false), "The fixture begins in the actual session countdown state");
            for (int i = 0; i < 400; i++) tickSession();
            require(surface() == INITIAL_SURFACE,
                    "Two rise periods spent in the match countdown do not advance Flood");
            require(block(probeX, INITIAL_SURFACE - 1, probeZ) == Material.AIR,
                    "Countdown callbacks do not paint queued water before gameplay starts");
            require(totalDeaths() == 0 && transport.values().stream().allMatch(p -> p.lookups == 0),
                    "Countdown callbacks do not even select players for Flood damage");

            set(session, "started", true);
            tickSession();
            require(submerged.healthCalls == 1 && submerged.lastHealth == 0,
                    "An already-submerged live participant dies without moving or producing a move event");
            require(barrier.healthCalls == 1 && boxed.healthCalls == 1,
                    "Standing on a solid barrier or in a sealed dry box is no refuge from the lethal height");
            require(block(x + 4, INITIAL_SURFACE - 1, z + 4) == Material.STONE,
                    "The real solid barrier is not replaced with water");
            require(boxIntact(x, z), "The sealed box's native solid floor, walls and ceiling are retained");
            require(block(x + 9, INITIAL_SURFACE, z + 9) == Material.AIR,
                    "The boxed participant is killed even while its actual feet block remains dry air");
            require(!alive.contains(submerged.rosterEntry) && !alive.contains(barrier.rosterEntry)
                            && !alive.contains(boxed.rosterEntry),
                    "Synchronous death removal does not skip neighboring entries in the actual reverse roster traversal");

            for (int activeTick = 2; activeTick <= 199; activeTick++) tickSession();
            require(surface() == INITIAL_SURFACE, "The plane remains at its initial height through active tick 199");
            require(block(probeX, INITIAL_SURFACE - 1, probeZ) == Material.WATER,
                    "The initial rendered source-water block has its top at feet minus twenty");
            require(block(probeX, INITIAL_SURFACE, probeZ) == Material.AIR,
                    "No next-layer water is painted before the two-hundredth active tick");
            require(stationary.healthCalls == 0 && !stationary.dead,
                    "A motionless player exactly one block above the initial plane survives the first 199 ticks");
            require(keeper.healthCalls == 0 && alreadyDead.healthCalls == 0,
                    "Above-water and already-dead roster entries are not killed");
            assertExcluded(spectator, outsider);

            Location stationaryBefore = stationary.feet.clone();
            tickSession();
            require(surface() == INITIAL_SURFACE + 1, "The first rise happens at exactly active tick 200");
            require(block(probeX, INITIAL_SURFACE, probeZ) == Material.WATER,
                    "The native water surface actually rises one block on active tick 200");
            require(block(probeX, INITIAL_SURFACE + 1, probeZ) == Material.AIR,
                    "Water stops below the new surface instead of painting an extra block above it");
            require(stationary.healthCalls == 1 && stationary.lastHealth == 0
                            && stationary.feet.equals(stationaryBefore),
                    "The rising plane eliminates the stationary player at equality without any movement callback");
            require(boxIntact(x, z), "Rising Flood still does not replace any native solid box blocks");
            assertExcluded(spectator, outsider);

            // Real ChunkLoadEvent delivery, not an invocation of the listener or a fake chunk.
            Chunk late = loadFreshChunk(100);
            int lateX = late.getX() * 16 + 2, lateZ = late.getZ() * 16 + 2;
            require(block(lateX, INITIAL_SURFACE - 1, lateZ) == Material.AIR
                            && block(lateX, INITIAL_SURFACE, lateZ) == Material.AIR,
                    "A genuinely newly loaded chunk starts without either missing water layer");
            int catchupTicks = (world.getLoadedChunks().length * 512 + 2047) / 2048 + 2;
            require(catchupTicks < 190, "The isolated fixture can drain its real render queue before a second rise");
            for (int i = 0; i < catchupTicks; i++) tickSession();
            require(block(lateX, INITIAL_SURFACE - 1, lateZ) == Material.WATER
                            && block(lateX, INITIAL_SURFACE, lateZ) == Material.WATER,
                    "The real chunk-load listener catches a new chunk up across every existing Flood layer");
            require(surface() == INITIAL_SURFACE + 1 && block(lateX, INITIAL_SURFACE + 1, lateZ) == Material.AIR,
                    "Chunk catch-up does not advance or overshoot the gameplay surface");

            BlockFromToEvent flowing = new BlockFromToEvent(
                    world.getBlockAt(lateX, INITIAL_SURFACE, lateZ), BlockFace.DOWN);
            Bukkit.getPluginManager().callEvent(flowing);
            require(flowing.isCancelled(), "The live native Flood listener cancels source-water flow below its flat surface");
            require(registeredFloodListeners() > 0, "The fixture exercises a registered native event listener");

            // Leave genuine work pending, then close the production component and its gameplay task.
            Chunk queued = loadFreshChunk(200);
            int queuedX = queued.getX() * 16 + 2, queuedZ = queued.getZ() * 16 + 2;
            require(block(queuedX, INITIAL_SURFACE - 1, queuedZ) == Material.AIR,
                    "There is unpainted native chunk work waiting at shutdown");
            callbackTask = Bukkit.getScheduler().runTaskTimer(smoke, () -> {
                try { tickSession(); }
                catch (Exception failure) { throw new IllegalStateException("Flood smoke callback", failure); }
            }, 1L, 1L);
            set(session, "floodTask", callbackTask);
            require(Bukkit.getScheduler().isQueued(callbackTask.getTaskId()),
                    "Shutdown starts with a genuinely scheduled native gameplay callback");
            invoke(closeFlood, session);
            require(callbackTask.isCancelled() && !Bukkit.getScheduler().isQueued(callbackTask.getTaskId())
                            && get(session, "floodTask") == null,
                    "Production closeFlood cancels and releases the actual scheduled gameplay callback");
            require(registeredFloodListeners() == 0, "Production close unregisters every Flood gameplay listener");
            int closedSurface = surface();
            int deathsBeforeClose = totalDeaths();
            Chunk afterClose = loadFreshChunk(300);
            int closedX = afterClose.getX() * 16 + 2, closedZ = afterClose.getZ() * 16 + 2;
            for (int i = 0; i < 250; i++) invoke(tickModifier, flood);
            require(surface() == closedSurface, "Closed Flood ignores subsequent modifier ticks without advancing time");
            require(block(queuedX, INITIAL_SURFACE - 1, queuedZ) == Material.AIR
                            && block(queuedX, INITIAL_SURFACE, queuedZ) == Material.AIR,
                    "Close abandons the existing paint queue rather than altering blocks after teardown");
            require(block(closedX, INITIAL_SURFACE - 1, closedZ) == Material.AIR
                            && block(closedX, INITIAL_SURFACE, closedZ) == Material.AIR,
                    "Chunks loaded after close cannot enqueue or receive water");
            // Model the normal ended session as well as testing the scheduler's actual cancellation.
            // A stale callback must not select a survivor who is now below the former plane.
            set(session, "ending", true);
            keeper.feet.setY(INITIAL_SURFACE - 2);
            for (int i = 0; i < 250; i++) tickSession();
            require(totalDeaths() == deathsBeforeClose && keeper.healthCalls == 0 && surface() == closedSurface,
                    "Actual ended-session callbacks do not select newly submerged survivors or restart closed Flood");
            // Idempotence is important when session startup failure and normal teardown overlap.
            invoke(closeFlood, session);
            require(callbackTask.isCancelled() && registeredFloodListeners() == 0,
                    "Repeated production cleanup leaves callbacks cancelled and listeners removed");
            smoke.getLogger().info("PILLARS_FLOOD_SMOKE_PASS: " + assertions + " assertions");
            return assertions;
        } finally {
            // Never let a failing assertion leave Bukkit's global server replaced for the next fixture.
            serverField.set(null, originalServer);
            try {
                if (callbackTask != null) callbackTask.cancel();
                if (flood != null) {
                    if (closeFlood != null && session != null) invoke(closeFlood, session);
                    else ((AutoCloseable) flood).close();
                }
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
        world.getChunkAt(0, 0);
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
        Constructor<?> constructor = floodType.getDeclaredConstructor(World.class, int.class);
        constructor.setAccessible(true);
        flood = constructor.newInstance(world, FEET_Y);
        set(session, "flood", flood);
        tickFlood = method(sessionType, "tickFlood");
        closeFlood = method(sessionType, "closeFlood");
        tickModifier = method(floodType, "tick");
        surfaceY = method(floodType, "surfaceY");
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
                        return player == null ? null : player.proxy;
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
        serverField.set(null, lookupServer);
        try { invoke(tickFlood, session); }
        finally { serverField.set(null, previous); }
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
        final UUID id = UUID.randomUUID();
        final String name;
        final Location feet;
        final Player proxy;
        Object rosterEntry;
        boolean dead;
        GameMode gameMode = GameMode.SURVIVAL;
        int healthCalls;
        int lookups;
        double lastHealth = Double.NaN;

        FakePlayer(String name, Location feet) {
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
                        case "isOnline" -> true;
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
