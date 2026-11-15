import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import me.petr1furious.potatominigames.games.finals.FinalsMapArena;
import me.petr1furious.potatominigames.games.finals.FinalsState;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Automatic Paper chunk/entity ticking with real players; only network delivery is recorded. */
public final class FinalsMarkerStartupSmoke {
    private final JavaPlugin smoke, main;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final List<Endpoint> endpoints = new ArrayList<>();
    private final Map<Endpoint, List<Display>> initial = new LinkedHashMap<>();
    private final Map<Integer, Integer> spawnCounts = new HashMap<>();
    private FixtureArena arena;
    private Object highlights;
    private Method update, clear;
    private BukkitTask task;
    private int ticks, stage, stageStarted, checks;
    private boolean finished;

    private FinalsMarkerStartupSmoke(JavaPlugin smoke, JavaPlugin main, Runnable success, Consumer<Throwable> failure) {
        this.smoke = smoke; this.main = main; this.success = success; this.failure = failure;
    }

    public static void run(JavaPlugin smoke, JavaPlugin main, Runnable success, Consumer<Throwable> failure) {
        FinalsMarkerStartupSmoke check = new FinalsMarkerStartupSmoke(smoke, main, success, failure);
        try { check.start(); } catch (Throwable error) { check.finish(error); }
    }

    private void start() throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        ServerLevel level = ((CraftWorld) world).getHandle();
        for (int x = 29; x <= 35; x++) for (int z = 29; z <= 33; z++) world.getChunkAt(x, z).load();
        endpoints.add(new Endpoint(level, "StartupRed", 500, 65, 498));
        endpoints.add(new Endpoint(level, "StartupBlue", 502, 65, 498));
        arena = new FixtureArena(world);
        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsHighlights", true,
                main.getClass().getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(JavaPlugin.class);
        constructor.setAccessible(true);
        highlights = constructor.newInstance(main);
        update = type.getDeclaredMethod("update", FinalsMapArena.class, FinalsState.Phase.class,
                int.class, Collection.class, Map.class, Function.class);
        update.setAccessible(true);
        clear = type.getDeclaredMethod("clear");
        clear.setAccessible(true);
        for (Endpoint endpoint : endpoints) {
            require(endpoint.player.isOnline() && world.getPlayers().contains(endpoint.player),
                    "The endpoint is a real online player registered in the native world");
            require(endpoint.handle.moonrise$getChunkLoader() != null,
                    "Native player registration creates its own chunk loader");
            require(!endpoint.player.isChunkSent(org.bukkit.Chunk.getChunkKey(endpoint.player.getLocation())),
                    "Startup begins before the player's first chunk is sent");
        }
        refresh();
        for (Endpoint endpoint : endpoints) {
            List<Display> markers = markers(endpoint);
            require(markers.size() == 2, "Each native player owns one vault label and one outline");
            initial.put(endpoint, markers);
            for (Display marker : markers) require(endpoint.player.canSee(marker), "Initial private permission is granted");
        }
        task = Bukkit.getScheduler().runTaskTimer(smoke, this::tick, 1, 1);
    }

    private void tick() {
        try {
            ticks++;
            if (ticks % 2 == 0) refresh();
            if (stage == 0) {
                if (!allDelivered()) {
                    require(ticks < 200, "Automatic initial marker delivery timed out: " + diagnostic());
                    return;
                }
                validateDelivery("Automatic startup");
                for (Endpoint endpoint : endpoints) for (Display marker : initial.get(endpoint))
                    spawnCounts.put(marker.getEntityId(), endpoint.wire.spawns.getOrDefault(marker.getEntityId(), 0));
                arena.vault = new Location(arena.world, 1000, 90, 500);
                refresh();
                stage = 1; stageStarted = ticks;
                return;
            }
            if (!positionsArrived()) {
                require(ticks - stageStarted < 100, "Automatic same-entity movement timed out at stage " + stage + ": " + diagnostic());
                return;
            }
            validateDelivery(stage == 1 ? "Distant projected movement" : "Return to nearby vault");
            for (Endpoint endpoint : endpoints) {
                require(markers(endpoint).equals(initial.get(endpoint)), "Movement preserves the original native displays");
                for (Display marker : initial.get(endpoint)) require(
                        endpoint.wire.spawns.getOrDefault(marker.getEntityId(), 0) == spawnCounts.get(marker.getEntityId()),
                        "Movement arrives without removing and respawning the display");
            }
            if (stage == 1) {
                arena.vault = new Location(arena.world, 500, 65, 500);
                refresh();
                stage = 2; stageStarted = ticks;
            } else {
                smoke.getLogger().info("FINALS_MARKER_STARTUP_SMOKE_PASS: " + checks
                        + " native assertions across " + ticks + " automatic server ticks");
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }

    private void refresh() throws Exception {
        List<Player> players = endpoints.stream().map(endpoint -> (Player) endpoint.player).toList();
        invoke(update, highlights, arena, null, -1, players,
                Map.of(players.get(0).getUniqueId(), 0, players.get(1).getUniqueId(), 1),
                (Function<Player, Location>) Player::getLocation);
    }

    private boolean allDelivered() {
        for (Endpoint endpoint : endpoints) {
            if (!endpoint.player.isChunkSent(org.bukkit.Chunk.getChunkKey(arena.vault))) return false;
            for (Display marker : initial.get(endpoint)) {
                if (!endpoint.wire.positions.containsKey(marker.getEntityId()) || !endpoint.wire.metadata.containsKey(marker.getEntityId())) return false;
                Location expected = marker instanceof TextDisplay ? arena.vault().add(0, 2, 0)
                        : new Location(arena.world, arena.vault.getBlockX(), arena.vault.getY(), arena.vault.getBlockZ());
                if (marker.getLocation().distanceSquared(expected) > .000001) return false;
            }
        }
        return positionsArrived();
    }

    private boolean positionsArrived() {
        for (Endpoint endpoint : endpoints) for (Display marker : initial.get(endpoint)) {
            Vec3 client = endpoint.wire.positions.get(marker.getEntityId());
            Location real = marker.getLocation();
            if (client == null || client.distanceToSqr(new Vec3(real.getX(), real.getY(), real.getZ())) > .000001) return false;
        }
        return true;
    }

    private void validateDelivery(String context) throws Exception {
        for (Endpoint owner : endpoints) for (Display marker : initial.get(owner)) {
            int id = marker.getEntityId();
            require(marker.isValid() && owner.player.canSee(marker), context + ": marker remains valid and privately visible");
            require(marker.getTrackedBy().contains(owner.player), context + ": native automatic tracker includes the owner");
            Map<Integer, Object> metadata = owner.wire.metadata.get(id);
            require(metadata != null, context + ": native metadata reaches the owner");
            if (marker instanceof TextDisplay) {
                Object value = metadata.get(accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_TEXT_ID"));
                require(value instanceof net.minecraft.network.chat.Component text && !text.getString().isEmpty(),
                        context + ": actual client metadata contains nonempty text");
            } else {
                Object flags = metadata.get(accessor(net.minecraft.world.entity.Entity.class, "DATA_SHARED_FLAGS_ID"));
                require(flags instanceof Byte value && (value & 0x40) != 0, context + ": actual metadata enables outline glow");
                Object value = metadata.get(accessor(net.minecraft.world.entity.Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID"));
                require(value instanceof net.minecraft.world.level.block.state.BlockState block
                        && block.is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK), context + ": actual metadata supplies the block model");
            }
            for (Endpoint other : endpoints) if (other != owner)
                require(!other.wire.positions.containsKey(id) && !other.wire.metadata.containsKey(id),
                        context + ": another contestant never receives this private display");
        }
    }

    private String diagnostic() {
        List<String> details = new ArrayList<>();
        for (Endpoint endpoint : endpoints) for (Display marker : initial.getOrDefault(endpoint, List.of())) {
            Location at = marker.getLocation();
            details.add(endpoint.player.getName() + ":" + marker.getType() + "#" + marker.getEntityId()
                    + " valid=" + marker.isValid() + " canSee=" + endpoint.player.canSee(marker)
                    + " tracked=" + marker.getTrackedBy().contains(endpoint.player)
                    + " chunkSent=" + endpoint.player.isChunkSent(org.bukkit.Chunk.getChunkKey(at))
                    + " server=" + at + " client=" + endpoint.wire.positions.get(marker.getEntityId())
                    + " packets=" + endpoint.wire.counts);
        }
        return details.toString();
    }

    private void finish(Throwable error) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        try { if (highlights != null && clear != null) invoke(clear, highlights); }
        catch (Throwable cleanup) { if (error == null) error = cleanup; else error.addSuppressed(cleanup); }
        for (Endpoint endpoint : endpoints) try { endpoint.close(); }
        catch (Throwable cleanup) { if (error == null) error = cleanup; else error.addSuppressed(cleanup); }
        if (error == null) success.run(); else failure.accept(error);
    }

    private List<Display> markers(Endpoint endpoint) throws Exception {
        Map<?, ?> views = (Map<?, ?>) field(highlights, "views");
        Map<?, ?> overlays = (Map<?, ?>) field(views.get(endpoint.player.getUniqueId()), "overlays");
        List<Display> result = new ArrayList<>();
        for (Object overlay : overlays.values()) {
            result.add((Display) field(overlay, "label"));
            Object outline = field(overlay, "outline");
            if (outline != null) result.add((Display) outline);
        }
        return result;
    }

    private static int accessor(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true);
        return ((EntityDataAccessor<?>) field.get(null)).id();
    }
    private static Object field(Object receiver, String name) throws Exception {
        Field field = receiver.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(receiver);
    }
    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }
    private void require(boolean condition, String description) {
        checks++; if (!condition) throw new AssertionError(description);
    }

    private static final class Endpoint implements AutoCloseable {
        final ServerLevel level;
        final ServerPlayer handle;
        final org.bukkit.craftbukkit.entity.CraftPlayer player;
        final RecordingConnection wire;
        final PlayerList registry;
        final List<ServerPlayer> registeredPlayers;
        @SuppressWarnings("unchecked") Endpoint(ServerLevel level, String name, double x, double y, double z) throws Exception {
            this.level = level;
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            handle.setPos(x, y, z); handle.setNoGravity(true);
            handle.moonrise$setRealPlayer(true);
            wire = new RecordingConnection(level);
            var listener = new ServerGamePacketListenerImpl(server, wire, handle, CommonListenerCookie.createInitial(profile, false));
            Field packetListener = Connection.class.getDeclaredField("packetListener");
            packetListener.setAccessible(true); packetListener.set(wire, listener);
            player = handle.getBukkitEntity();
            player.setInvulnerable(true);
            registry = server.getPlayerList();
            Field list = PlayerList.class.getDeclaredField("players"); list.setAccessible(true);
            registeredPlayers = (List<ServerPlayer>) list.get(registry);
            registry.getPlayersByUUID().put(profile.id(), handle);
            registeredPlayers.add(handle);
            try {
                level.addNewPlayer(handle);
                player.setSendViewDistance(3);
                player.setViewDistance(3);
                if (handle.moonrise$getChunkLoader() == null)
                    level.moonrise$getPlayerChunkLoader().addPlayer(handle);
            } catch (Throwable error) { close(); throw error; }
        }
        @Override public void close() {
            if (handle.moonrise$getChunkLoader() != null)
                level.moonrise$getPlayerChunkLoader().removePlayer(handle);
            level.removePlayerImmediately(handle, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            registeredPlayers.remove(handle); registry.getPlayersByUUID().remove(handle.getUUID(), handle);
            wire.channel.close(); handle.getTextFilter().leave();
        }
    }

    private static final class RecordingConnection extends Connection {
        final ServerLevel level;
        final Map<Integer, Vec3> positions = new HashMap<>();
        final Map<Integer, Map<Integer, Object>> metadata = new HashMap<>();
        final Map<Integer, Integer> spawns = new HashMap<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        RecordingConnection(ServerLevel level) { super(PacketFlow.SERVERBOUND); this.level = level; channel = new EmbeddedChannel(); }
        @Override public void send(Packet<?> packet) { record(packet); }
        @Override public void send(Packet<?> packet, ChannelFutureListener callback) { record(packet); }
        @Override public void send(Packet<?> packet, ChannelFutureListener callback, boolean flush) { record(packet); }
        private void record(Packet<?> packet) {
            if (packet instanceof BundlePacket<?> bundle) { bundle.subPackets().forEach(this::record); return; }
            counts.merge(packet.getClass().getSimpleName(), 1, Integer::sum);
            if (packet instanceof ClientboundAddEntityPacket add) {
                positions.put(add.getId(), new Vec3(add.getX(), add.getY(), add.getZ()));
                spawns.merge(add.getId(), 1, Integer::sum);
            } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
                for (int id : remove.entityIds()) {
                    positions.remove(id);
                    metadata.remove(id);
                }
            } else if (packet instanceof ClientboundSetEntityDataPacket data) {
                Map<Integer, Object> values = metadata.computeIfAbsent(data.id(), ignored -> new HashMap<>());
                data.packedItems().forEach(value -> values.put(value.id(), value.value()));
            } else if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
                if (positions.containsKey(sync.id())) positions.put(sync.id(), sync.position().endPosition());
            } else if (packet instanceof ClientboundTeleportEntityPacket teleport) {
                Vec3 previous = positions.get(teleport.id());
                if (previous != null) positions.put(teleport.id(), PositionMoveRotation.calculateAbsolute(
                        new PositionMoveRotation(previous, Vec3.ZERO, 0, 0), teleport.change(), teleport.relatives()).position());
            } else if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
                net.minecraft.world.entity.Entity entity = move.getEntity(level);
                if (entity != null && positions.containsKey(entity.getId())) {
                    VecDeltaCodec codec = new VecDeltaCodec(); codec.setBase(positions.get(entity.getId()));
                    positions.put(entity.getId(), move.getPositionDelta().decode(codec).endPosition());
                }
            }
        }
    }

    private static final class FixtureArena implements FinalsMapArena {
        final World world; Location vault;
        FixtureArena(World world) { this.world = world; vault = new Location(world, 500, 65, 500); }
        public World world() { return world; }
        public void build() {}
        public Location center() { return new Location(world, 500, 65, 500); }
        public Location spawn(int team, int memberIndex) { return center(); }
        public Location vault() { return vault.clone(); }
        public List<Location> vaultLocations() { return List.of(vault()); }
        public void nextVault() {}
        public Location station(int station) { return center().add(station * 8, 0, 0); }
        public List<Location> cashoutLocations() { return List.of(station(0), station(1)); }
        public void nextCashout(int station) {}
        public boolean contains(Location location) { return world.equals(location.getWorld()); }
        public boolean isCover(Block block) { return false; }
        public int stationAt(Block block) { return -1; }
        public boolean isVault(Block block) { return false; }
        public double borderSize() { return 4096; }
    }
}
