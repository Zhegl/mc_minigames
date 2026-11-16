import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.zip.ZipFile;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.GamePhase;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.games.pillars.PillarsConfig;
import me.petr1furious.potatominigames.games.pillars.PillarsGame;
import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketBundleUnpacker;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Destructive native ASP 26.3 / Java 25 fixture: ONLY run in a disposable server.
 *
 * Compile with Java 25 against the built PotatoMinigames JAR, the native remapped
 * versions/26.3/aspaper-26.3.jar, and every JAR recursively under the runtime's
 * libraries directory (plus cached JetBrains annotations if not there). No vendor
 * API is linked or shaded. Example once CP contains those colon-separated paths:
 *   javac -encoding UTF-8 -cp "$CP" -d /tmp/replay-classes native-checks/pillars/PillarsReplaySmoke.java
 *   cp native-checks/pillars/replay-plugin.yml /tmp/replay-classes/plugin.yml
 *   jar --create --file /tmp/PillarsReplaySmoke.jar -C /tmp/replay-classes .
 *
 * Put that JAR, the current main JAR and pinned FlashbackServer-1.2.4.jar in the
 * isolated server's plugins directory. Copy the cached ASP bootstrap, versions,
 * libraries and cache; accept EULA there. Use online-mode=false, loopback binding,
 * an unused port, view-distance=3, simulation-distance=3,
 * pause-when-empty-seconds=-1, no RCON/query/status/management, and main plugin
 * lobby.enabled=false / asp.enable=false. No imported production arena is needed.
 * Start from that directory with Java 25:
 *   java -Xms256M -Xmx1536M -Dpillars.replaySmoke=true -jar asp-server.jar --nogui
 * A cached eclipse-temurin:25-jdk container works with this whole isolated root
 * mounted read-write and its working directory set there; publish no ports.
 * DO NOT mount the live production /data read-write or replace its active JARs.
 *
 * Two real SessionManager/PillarsSession rounds use a fixture-only empty ASP
 * template: original + forced stop with quit/rejoin segments, then Flood + normal
 * time-limit completion. Real CraftPlayers, native world/chunk/entity ticking,
 * Connection.send, PacketBundleUnpacker and PacketEncoder are used. Only the
 * socket/login client is replaced by an EmbeddedChannel, acknowledging server
 * teleports/chunk batches on subsequent fixture ticks. Movement changes native
 * ServerPlayer positions; this is not a player-input/anti-cheat/protocol-login test.
 * Quit is a real Bukkit event dispatch followed by native player removal; rejoin
 * uses a new native connection with the same UUID and SessionManager.handleJoin.
 * No TCP disconnect/login handshake or rendered Fabric playback is claimed.
 *
 * Saves are awaited off-thread while the server keeps ticking. Every expected POV
 * is verified through installed FlashbackAPI.verify, then ZIP metadata, snapshot
 * packets and stream actions are inspected after its arena has unloaded and its
 * folder is deleted. Logs report compressed bytes, uncompressed ZIP-entry bytes,
 * payload bytes and ticks: these quantify THIS short synthetic sample, not heap
 * consumption, production replay size or long-match memory safety. Replays and
 * result.txt are intentionally retained as evidence; only own players/worlds,
 * listener and unique Slime template are cleaned. The isolated server shuts down.
 */
public final class PillarsReplaySmoke extends JavaPlugin implements Listener {
    private static final BlockPos MARKER = new BlockPos(0, 203, 0);
    private static final BlockPos LOBBY_MARKER = new BlockPos(13, 220, 13);
    private static final String GAME = "flashback:action/game_packet";
    private static final String NEXT = "flashback:action/next_tick";
    private static final String MOVE = "flashback:action/move_entities";
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private final List<Endpoint> endpoints = new ArrayList<>();
    private final List<Round> rounds = new ArrayList<>();
    private final List<String> evidence = new ArrayList<>();
    private JavaPlugin main;
    private Plugin vendor;
    private Class<?> api, recordingType, chunkReader;
    private Object recording;
    private SessionManager manager;
    private SlimeWorldService slime;
    private PillarsGame game;
    private ActiveSession active;
    private Endpoint a, b, outsider;
    private Round round;
    private World lobby;
    private String template, arenaName;
    private BukkitTask ticker;
    private CompletableFuture<Void> saves;
    private Set<Path> baseline;
    private int ticks, stage, since, assertions;
    private int unloads;
    private boolean finishing, templateCreated;
    private Throwable problem;

    private static final class Round {
        final World world;
        final String dimension;
        final Path folder, directory;
        final boolean flood;
        final List<View> views = new ArrayList<>();
        Round(World world, Path directory, boolean flood) {
            this.world = world; this.directory = directory; this.flood = flood;
            folder = world.getWorldFolder().toPath();
            dimension = ((CraftWorld) world).getHandle().dimension().identifier().toString();
        }
    }
    private record View(Path path, UUID playerId, int entityId) {}
    private interface Check { void run() throws Exception; }

    @Override public void onEnable() {
        if (!Boolean.getBoolean("pillars.replaySmoke")) {
            getLogger().severe("Replay smoke disabled: requires -Dpillars.replaySmoke=true on an ISOLATED disposable server");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> guard(this::start));
    }

    private void start() throws Exception {
        require(Bukkit.isPrimaryThread(), "Fixture starts on the native server thread");
        require(Bukkit.getMinecraftVersion().equals("26.3"), "Fixture is pinned to Minecraft 26.3");
        require(Bukkit.getOnlinePlayers().isEmpty(), "Isolated fixture refuses an existing online roster");
        main = (JavaPlugin) Bukkit.getPluginManager().getPlugin("PotatoMinigames");
        vendor = Bukkit.getPluginManager().getPlugin("FlashbackServer");
        require(main != null && main.isEnabled() && vendor != null && vendor.isEnabled(), "Both real plugins loaded");
        require(vendor.getPluginMeta().getVersion().equals("1.2.4"), "Recorder version is pinned to 1.2.4");
        ClassLoader loader = vendor.getClass().getClassLoader();
        api = loader.loadClass("dev.zeffut.flashbackserver.api.FlashbackAPI");
        recordingType = loader.loadClass("dev.zeffut.flashbackserver.api.RecordingService");
        chunkReader = loader.loadClass("dev.zeffut.flashbackserver.format.ChunkReader");
        require(Boolean.TRUE.equals(api.getMethod("isAvailable").invoke(null)), "Real vendor adapter is available");
        recording = api.getMethod("recording").invoke(null);
        baseline = replayFiles();
        manager = (SessionManager) field(main, "sessions");
        require(manager.activeSessions().isEmpty(), "No pre-existing game is touched");
        slime = (SlimeWorldService) field(manager, "slimeWorlds");
        template = "replay_smoke_" + suffix;
        arenaName = "replay_smoke_" + suffix;
        require(!slime.templateExists(template), "Unique fixture template does not overwrite anything");
        slime.createEmptyTemplate(template, 0, 65, 0);
        templateCreated = true;
        ConfigRegistry configs = new ConfigRegistry(this);
        game = new PillarsGame(main, configs, slime);
        PillarsConfig config = (PillarsConfig) field(game, "config");
        setProperty(config, "startCountdown", 0);
        setProperty(config, "platformHeight", 200.0);
        setProperty(config, "enchantChance", 0.0);
        setProperty(config, "reconnectGraceSeconds", 120);
        var yaml = configs.file("games/pillars.yml");
        yaml.set("arenas", null);
        yaml.set("arenas." + arenaName + ".template", template);
        yaml.set("arenas." + arenaName + ".x", 0);
        yaml.set("arenas." + arenaName + ".z", 0);
        yaml.set("items", null);
        yaml.set("items.blacklist-patterns", List.of("*"));
        yaml.set("items.blacklist-exceptions", List.of("stone"));
        for (String mode : List.of("original", "flood")) {
            setProperty(config.mode(mode), "cooldownSeconds", 600);
            setProperty(config.mode(mode), "timeLimit", "10min");
            setProperty(config.mode(mode), "bossBar", false);
            setProperty(config.mode(mode), "scoreboard", false);
            setProperty(config.mode(mode), "actionBar", false);
        }
        lobby = Bukkit.getWorlds().getFirst();
        ServerLevel level = ((CraftWorld) lobby).getHandle();
        a = endpoint(level, UUID.randomUUID(), "ReplaySmokeA");
        b = endpoint(level, UUID.randomUUID(), "ReplaySmokeB");
        outsider = endpoint(level, UUID.randomUUID(), "ReplayOutside");
        Bukkit.getPluginManager().registerEvents(this, this);
        ticker = Bukkit.getScheduler().runTaskTimer(this, () -> guard(this::tick), 1, 1);
    }

    private Endpoint endpoint(ServerLevel level, UUID id, String name) throws Exception {
        Endpoint endpoint = new Endpoint(level, id, name);
        endpoints.add(endpoint);
        return endpoint;
    }

    private void tick() throws Exception {
        ticks++;
        for (Endpoint endpoint : endpoints) if (!endpoint.closed) endpoint.pump();
        if (finishing) { finishWhenSaved(); return; }
        require(ticks < 1800, "Fixture completes within 1800 server ticks; stage=" + stage);
        require(!isRecording(outsider.player), "A native lobby nonparticipant is never recorded");
        for (Endpoint endpoint : endpoints) require(!endpoint.realWater, "Flood water packets do not come from real water blocks");
        int elapsed = ticks - since;
        switch (stage) {
            case 0 -> {
                require(!isRecording(a.player) && !isRecording(b.player), "Lobby players are not recorded before a match");
                if (elapsed < 8) return;
                require(replayFiles().equals(baseline), "Lobby activity creates no replay file");
                startRound(false);
                advance(1);
            }
            case 1 -> {
                if (!ready()) { require(elapsed < 300, "Native initial chunks/snapshots become ready"); return; }
                nativeBlock(Material.DIAMOND_BLOCK);
                advance(2);
            }
            case 2 -> {
                moveParticipants();
                if (elapsed < 24) return;
                require(a.markerPackets > 0 && b.markerPackets > 0, "Both POVs receive the real native block mutation");
                UUID id = a.player.getUniqueId();
                Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(a.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
                require(!isRecording(a.player) && isRecording(b.player), "LOWEST quit stops only departing POV before vendor auto-stop");
                a.close();
                require(Bukkit.getPlayer(id) == null, "Native disconnected player leaves UUID lookup");
                advance(3);
            }
            case 3 -> {
                moveParticipants();
                if (elapsed < 8) return;
                a = endpoint(((CraftWorld) lobby).getHandle(), a.player.getUniqueId(), "ReplaySmokeA");
                require(!isRecording(a.player) && a.player.getWorld() == lobby, "Replacement connection starts unrecorded in lobby");
                require(manager.handleJoin(a.player), "Production session manager recognizes reconnecting participant");
                advance(4);
            }
            case 4 -> {
                if (a.player.getWorld() != round.world || !isRecording(a.player)) {
                    require(elapsed < 60, "Production rejoin returns participant to arena before starting next segment"); return;
                }
                require(a.wire != endpoints.getFirst().wire, "Rejoin uses a different native Netty connection");
                addView(a, 2);
                if (!ready()) { require(elapsed < 300, "Rejoined native chunks become ready"); return; }
                nativeBlock(Material.LAPIS_BLOCK);
                advance(5);
            }
            case 5 -> {
                moveParticipants();
                if (elapsed < 24) return;
                require(a.markerPackets > 0, "Rejoined POV receives a real block event after its snapshot");
                active.stopNow(EndReason.ABORTED);
                require(active.phase() == GamePhase.IDLE, "Forced end runs complete production cleanup");
                afterEnd();
                advance(6);
            }
            case 6 -> {
                assertLobbyStopped();
                if (!saves.isDone()) { require(elapsed < 300, "Forced-end asynchronous writes complete"); return; }
                saves.join();
                verifyRound(round);
                startRound(true);
                advance(7);
            }
            case 7 -> {
                if (!ready() || !Boolean.TRUE.equals(field(active.gameSession(), "started"))) {
                    require(elapsed < 400, "Flood preparation finishes through real session ticks"); return;
                }
                nativeBlock(Material.DIAMOND_BLOCK);
                advance(8);
            }
            case 8 -> {
                moveParticipants();
                if (elapsed < 30) return;
                require(a.fakeWater > 0 && b.fakeWater > 0, "Production Flood sends client-only water through both native connections");
                invoke(active.gameSession(), "setTime", new Class<?>[] {long.class}, 0L);
                advance(9);
            }
            case 9 -> {
                if (active.phase() != GamePhase.IDLE) { require(elapsed < 80, "Natural time-limit end cleans the production session"); return; }
                require(field(active.gameSession(), "endingCause").toString().equals("TIME_OVER"), "Natural round uses actual TIME_OVER path");
                afterEnd();
                advance(10);
            }
            case 10 -> {
                assertLobbyStopped();
                if (!saves.isDone()) { require(elapsed < 300, "Natural-end asynchronous writes complete"); return; }
                saves.join();
                slime.fileLoader().deleteWorld(template);
                templateCreated = false;
                require(!slime.templateExists(template), "The source template is deleted before final playback verification");
                for (Round match : rounds) verifyRound(match);
                require(unloads == 2, "Both native arena unload events observed captures already stopped");
                Set<Path> added = replayFiles(); added.removeAll(baseline);
                Set<Path> expected = new HashSet<>();
                for (Round match : rounds) for (View view : match.views) expected.add(view.path());
                require(added.equals(expected), "Only five expected match POV segments exist; no vendor-default/lobby/duplicate saves: " + added);
                finish(null);
            }
            default -> throw new AssertionError("Unknown fixture stage " + stage);
        }
    }

    private void advance(int next) { stage = next; since = ticks; }

    private void startRound(boolean flood) throws Exception {
        assertLobbyStopped();
        a.markerPackets = b.markerPackets = a.fakeWater = b.fakeWater = 0;
        active = manager.start(game, flood ? "flood" : "original", List.of(a.player, b.player), Map.of(),
                false, Map.of("arena", arenaName, "test", "true"));
        World world = (World) field(active.gameSession(), "world");
        require(world != lobby && active.owns(world), "Production PillarsSession creates an owned real ASP clone");
        Object replays = field(active.gameSession(), "replays");
        require(replays != null, "Installed vendor is used by production session integration");
        round = new Round(world, (Path) field(replays, "directory"), flood);
        rounds.add(round);
        require(round.directory.startsWith(vendor.getDataFolder().toPath().toAbsolutePath().resolve("replays/pillars")),
                "Recordings are outside the disposable arena");
        require(rounds.size() == 1 || !round.directory.equals(rounds.getFirst().directory), "Repeated games never overwrite match directories");
        for (Endpoint endpoint : List.of(a, b)) {
            endpoint.handle.setNoGravity(true);
            require(endpoint.player.getWorld() == world && isRecording(endpoint.player), "Participant capture starts only after native arena teleport");
            addView(endpoint, 1);
        }
        require(((List<?>) field(active.gameSession(), "items")).size() == 1, "Deterministic fixture pool contains only stone, no random hazards");
    }

    private void addView(Endpoint endpoint, int part) {
        Path path = round.directory.resolve(endpoint.player.getUniqueId() + "-part" + part + ".flashback");
        if (round.views.stream().noneMatch(view -> view.path().equals(path)))
            round.views.add(new View(path, endpoint.player.getUniqueId(), endpoint.handle.getId()));
    }

    private boolean ready() throws Exception {
        for (Endpoint endpoint : List.of(a, b)) {
            if (endpoint.closed || endpoint.player.getWorld() != round.world || !isRecording(endpoint.player)) return false;
            endpoint.embedded.runPendingTasks();
            require(endpoint.embedded.pipeline().get("flashback_capture_raw") != null,
                    "Actual vendor capture handler is installed after native encoder");
            if (!endpoint.player.isChunkSent(org.bukkit.Chunk.getChunkKey(new Location(round.world, 0, 203, 0)))) return false;
            if (ticks - since < 8) return false;
        }
        return true;
    }

    private void nativeBlock(Material material) {
        round.world.getBlockAt(MARKER.getX(), MARKER.getY(), MARKER.getZ()).setType(material, false);
    }

    private void moveParticipants() {
        for (Endpoint endpoint : List.of(a, b)) if (!endpoint.closed && endpoint.player.getWorld() == round.world) {
            endpoint.handle.setNoGravity(true);
            // Native position sampling generates Flashback move_entities, not fabricated replay bytes.
            endpoint.handle.setPos(endpoint.handle.getX() + .015, endpoint.handle.getY(), endpoint.handle.getZ());
            endpoint.handle.setYRot(endpoint.handle.getYRot() + 2);
        }
    }

    private void afterEnd() throws Exception {
        require(Bukkit.getWorld(round.world.getUID()) == null, "Ended arena has actually unloaded");
        for (Endpoint endpoint : List.of(a, b)) {
            require(!isRecording(endpoint.player) && endpoint.player.getWorld() != round.world,
                    "End stops capture before native arena evacuation/unload");
            endpoint.wire.send(new ClientboundBlockUpdatePacket(LOBBY_MARKER, Blocks.GOLD_BLOCK.defaultBlockState()));
        }
        deleteTree(round.folder);
        require(!Files.exists(round.folder), "Deleted arena folder cannot supply any playback data");
        saves = flushSaves();
    }

    private void assertLobbyStopped() throws Exception {
        for (Endpoint endpoint : List.of(a, b, outsider)) if (!endpoint.closed)
            require(!isRecording(endpoint.player) && endpoint.player.getWorld() == lobby,
                    "No participant or outsider capture remains in lobby");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(WorldUnloadEvent event) {
        if (round == null || event.getWorld() != round.world) return;
        try {
            for (Endpoint endpoint : endpoints) if (!endpoint.closed)
                require(!isRecording(endpoint.player), "Recording is stopped at native WorldUnloadEvent, before deletion");
            unloads++;
        } catch (Throwable error) { if (problem == null) problem = error; }
    }

    private boolean isRecording(Player player) throws Exception {
        return Boolean.TRUE.equals(recordingType.getMethod("isRecording", Player.class).invoke(recording, player));
    }

    private CompletableFuture<Void> flushSaves() throws Exception {
        Method flush = main.getClass().getClassLoader().loadClass(
                "me.petr1furious.potatominigames.games.pillars.PillarsReplays").getMethod("awaitSaves");
        return CompletableFuture.runAsync(() -> {
            try { flush.invoke(null); }
            catch (ReflectiveOperationException error) { throw new java.util.concurrent.CompletionException(error); }
        });
    }

    private Set<Path> replayFiles() throws Exception {
        Path root = vendor.getDataFolder().toPath().toAbsolutePath().resolve("replays");
        if (!Files.exists(root)) return new HashSet<>();
        try (var files = Files.walk(root)) {
            return new HashSet<>(files.filter(path -> path.toString().endsWith(".flashback")).toList());
        }
    }

    private void verifyRound(Round match) throws Exception {
        require(Bukkit.getWorld(match.world.getUID()) == null && !Files.exists(match.folder),
                "Verification occurs after native arena unload AND folder deletion");
        for (View view : match.views) {
            require(Files.isRegularFile(view.path()) && Files.size(view.path()) > 0, "Actual POV ZIP was saved: " + view.path());
            Object checked = api.getMethod("verify", Path.class).invoke(null, view.path());
            require(Boolean.TRUE.equals(invoke(checked, "valid")) && Boolean.TRUE.equals(invoke(checked, "formatValid"))
                            && Boolean.TRUE.equals(invoke(checked, "decodeClean")) && ((Integer) invoke(checked, "errorCount")) == 0,
                    "Installed FlashbackAPI verifies native packet decoding: " + checked);
            inspectZip(match, view, checked);
        }
    }

    private void inspectZip(Round match, View view, Object checked) throws Exception {
        int nextTicks = 0, snapshotConfigs = 0, recordedChunks = 0, snapshotLogins = 0;
        int streamPackets = 0, markers = 0, water = 0, moves = 0;
        long payloadBytes = 0, uncompressedBytes = 0;
        Set<String> positions = new HashSet<>();
        var codec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(((CraftServer) Bukkit.getServer()).getServer().registryAccess())).codec();
        try (ZipFile zip = new ZipFile(view.path().toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                long size = entries.nextElement().getSize();
                require(size >= 0, "ZIP central directory reports an actual uncompressed size");
                uncompressedBytes += size;
            }
            var metadataEntry = zip.getEntry("metadata.json");
            require(metadataEntry != null, "Replay contains metadata.json");
            JsonObject metadata;
            try (var input = zip.getInputStream(metadataEntry)) {
                metadata = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            require(metadata.get("protocol_version").getAsInt() == SharedConstants.getProtocolVersion(), "ZIP protocol matches real 26.3 runtime");
            require(metadata.get("data_version").getAsInt() == Bukkit.getUnsafe().getDataVersion(), "ZIP data version matches native runtime");
            JsonObject chunks = metadata.getAsJsonObject("chunks");
            require(chunks != null && !chunks.isEmpty(), "ZIP metadata references real replay chunks");
            int durations = 0;
            for (var chunk : chunks.entrySet()) {
                durations += chunk.getValue().getAsJsonObject().get("duration").getAsInt();
                var entry = zip.getEntry(chunk.getKey());
                require(entry != null, "Every metadata chunk exists in the ZIP");
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readAllBytes(); }
                Object parsed = chunkReader.getMethod("read", byte[].class).invoke(null, (Object) bytes);
                for (boolean snapshot : new boolean[] {true, false}) {
                    List<?> actions = (List<?>) invoke(parsed, snapshot ? "snapshotActions" : "streamActions");
                    for (Object action : actions) {
                        String identifier = (String) invoke(action, "identifier");
                        byte[] payload = (byte[]) invoke(action, "payload");
                        payloadBytes += payload.length;
                        if (snapshot && identifier.equals("flashback:action/configuration_packet")) snapshotConfigs++;
                        if (!snapshot && identifier.equals(NEXT)) nextTicks++;
                        if (!snapshot && identifier.equals(MOVE)) {
                            moves++;
                            readMovement(payload, match, view, positions);
                        }
                        if (!identifier.equals(GAME)) continue;
                        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
                        Packet<?> packet;
                        try {
                            packet = codec.decode(buffer);
                            require(!buffer.isReadable(), "Native game codec consumes the complete recorded action");
                        } finally { buffer.release(); }
                        if (packet instanceof ClientboundLevelChunkWithLightPacket) recordedChunks++;
                        if (snapshot) {
                            if (packet instanceof ClientboundLoginPacket login) {
                                require(login.commonPlayerSpawnInfo().dimension().identifier().toString().equals(match.dimension),
                                        "Snapshot logs into the deleted ARENA dimension, never the lobby");
                                snapshotLogins++;
                            }
                        } else {
                            streamPackets++;
                            if (packet instanceof ClientboundBlockUpdatePacket block) {
                                require(!block.getPos().equals(LOBBY_MARKER) || !block.getBlockState().is(Blocks.GOLD_BLOCK),
                                        "Post-game lobby sentinel is absent from recorded stream");
                                if (block.getPos().equals(MARKER) && (block.getBlockState().is(Blocks.DIAMOND_BLOCK)
                                        || block.getBlockState().is(Blocks.LAPIS_BLOCK))) markers++;
                                if (block.getBlockState().is(Blocks.WATER)) water++;
                            }
                            if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
                                int[] counts = new int[2];
                                section.runUpdates((pos, state) -> {
                                    if (pos.equals(MARKER) && (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.LAPIS_BLOCK))) counts[0]++;
                                    if (state.is(Blocks.WATER)) counts[1]++;
                                    if (pos.equals(LOBBY_MARKER) && state.is(Blocks.GOLD_BLOCK))
                                        throw new AssertionError("Lobby sentinel leaked into a section update");
                                });
                                markers += counts[0]; water += counts[1];
                            }
                        }
                    }
                }
            }
            require(nextTicks >= 5 && nextTicks == durations && nextTicks == metadata.get("total_ticks").getAsInt()
                            && nextTicks == (Integer) invoke(checked, "totalTicks"), "Real next_tick actions agree with ZIP durations and vendor verifier");
            require(snapshotConfigs > 0 && snapshotLogins == 1 && recordedChunks > 0,
                    "Recording carries configuration, login and terrain independently of the deleted arena");
            require(streamPackets > 0 && markers > 0, "POV contains native block events in STREAM, not just a start snapshot");
            require(moves >= 2 && positions.size() >= 2, "POV stream contains changing positions for its actual native player entity");
            if (match.flood) require(water > 0, "Decoded replay STREAM preserves actual client-only Flood water packets");
        }
        String result = "PILLARS_REPLAY_POV: mode=" + (match.flood ? "flood" : "original")
                + " player=" + view.playerId() + " file=" + view.path()
                + " bytes=" + Files.size(view.path()) + " uncompressedZipBytes=" + uncompressedBytes
                + " actionPayloadBytes=" + payloadBytes + " ticks=" + nextTicks + " streamPackets=" + streamPackets
                + " movementActions=" + moves + " distinctOwnPositions=" + positions.size()
                + " nativeBlockEvents=" + markers + " fakeWaterCells=" + water;
        getLogger().info(result);
        if (!evidence.contains(result)) evidence.add(result);
    }

    private void readMovement(byte[] payload, Round match, View view, Set<String> positions) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int dimensions = varInt(input);
            for (int dimension = 0; dimension < dimensions; dimension++) {
                int length = varInt(input);
                String key = new String(input.readNBytes(length), StandardCharsets.UTF_8);
                require(key.equals(match.dimension), "Movement actions never escape the recorded arena into lobby");
                int entities = varInt(input);
                for (int i = 0; i < entities; i++) {
                    int id = varInt(input);
                    double x = input.readDouble(), y = input.readDouble(), z = input.readDouble();
                    float yaw = input.readFloat(); input.readFloat(); input.readFloat(); input.readBoolean();
                    if (id == view.entityId()) positions.add(x + ":" + y + ":" + z + ":" + yaw);
                }
            }
            require(input.available() == 0, "Pinned move_entities payload is fully consumed");
        }
    }

    private static int varInt(DataInputStream input) throws Exception {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = input.readUnsignedByte(); value |= (next & 127) << shift;
            if ((next & 128) == 0) return value;
        }
        throw new AssertionError("Overlong recorded VarInt");
    }

    private void guard(Check check) {
        try { check.run(); }
        catch (Throwable error) { finish(error); }
    }

    private void finish(Throwable error) {
        if (finishing) {
            if (error != null) {
                if (problem == null) problem = error;
                else if (problem != error) problem.addSuppressed(error);
                complete();
            }
            return;
        }
        finishing = true;
        if (problem == null) problem = error;
        else if (error != null && problem != error) problem.addSuppressed(error);
        try { if (active != null && active.phase() != GamePhase.IDLE) active.stopNow(EndReason.ABORTED); }
        catch (Throwable cleanup) { if (problem == null) problem = cleanup; else problem.addSuppressed(cleanup); }
        try { saves = main == null ? CompletableFuture.completedFuture(null) : flushSaves(); }
        catch (Throwable cleanup) { if (problem == null) problem = cleanup; else problem.addSuppressed(cleanup); saves = CompletableFuture.completedFuture(null); }
        since = ticks;
        if (ticker == null) ticker = Bukkit.getScheduler().runTaskTimer(this, () -> guard(this::tick), 1, 1);
    }

    private void finishWhenSaved() {
        if (!saves.isDone() && ticks - since < 300) return;
        try {
            require(saves.isDone(), "Final asynchronous saves complete before shutdown");
            saves.join();
        } catch (Throwable error) { if (problem == null) problem = error; else problem.addSuppressed(error); }
        complete();
    }

    private void complete() {
        if (ticker != null) ticker.cancel();
        HandlerList.unregisterAll((Listener) this);
        if (game != null) HandlerList.unregisterAll(game);
        for (Endpoint endpoint : endpoints) {
            try { endpoint.close(); }
            catch (Throwable error) { if (problem == null) problem = error; else problem.addSuppressed(error); }
        }
        for (Round match : rounds) {
            try {
                if (Bukkit.getWorld(match.world.getUID()) != null)
                    require(Bukkit.unloadWorld(match.world, false), "Failure cleanup unloads own arena");
                deleteTree(match.folder);
            } catch (Throwable error) { if (problem == null) problem = error; else problem.addSuppressed(error); }
        }
        try { if (templateCreated) { slime.fileLoader().deleteWorld(template); templateCreated = false; } }
        catch (Throwable error) { if (problem == null) problem = error; else problem.addSuppressed(error); }
        String result = problem == null ? "PILLARS_REPLAY_SMOKE_PASS: " + assertions + " native assertions, "
                + ticks + " server ticks, five saved POV segments, two deleted arenas\n"
                : "PILLARS_REPLAY_SMOKE_FAIL: " + problem + "\n";
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("result.txt"), result + String.join("\n", evidence) + "\n");
        } catch (Throwable error) {
            if (problem == null) problem = error; else problem.addSuppressed(error);
            result = "PILLARS_REPLAY_SMOKE_FAIL: cannot persist result\n";
        }
        if (problem == null) getLogger().info(result.trim());
        else getLogger().log(Level.SEVERE, result.trim(), problem);
        Bukkit.shutdown();
    }

    private static void deleteTree(Path directory) throws Exception {
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void setProperty(Object config, String name, T value) throws Exception {
        ((Property<T>) field(config, name)).set(value);
    }

    private static Object field(Object receiver, String name) throws Exception {
        Field field = receiver.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(receiver);
    }

    private static Object invoke(Object receiver, String name) throws Exception {
        return invoke(receiver, name, new Class<?>[0]);
    }

    private static Object invoke(Object receiver, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = receiver.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }

    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    /** Same real-player/world registry setup as FinalsMarkerStartupSmoke; no Player proxy. */
    private final class Endpoint implements AutoCloseable {
        final ServerPlayer handle;
        final CraftPlayer player;
        final Connection wire;
        final EmbeddedChannel embedded;
        final PlayerList registry;
        final List<ServerPlayer> registeredPlayers;
        final List<Integer> teleports = new ArrayList<>();
        boolean batchAck, realWater, closed;
        int markerPackets, fakeWater;

        @SuppressWarnings("unchecked")
        Endpoint(ServerLevel initial, UUID id, String name) throws Exception {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            GameProfile profile = new GameProfile(id, name);
            handle = new ServerPlayer(server, initial, profile, ClientInformation.createDefault());
            handle.setPos(.5, 65, .5); handle.setNoGravity(true); handle.moonrise$setRealPlayer(true);
            wire = new Connection(PacketFlow.SERVERBOUND);
            embedded = new EmbeddedChannel();
            wire.channel = embedded;
            wire.address = new InetSocketAddress("127.0.0.1", 0);
            wire.preparing = false;
            wire.isPending = false;
            var protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(initial.registryAccess()));
            embedded.pipeline().addLast("encoder", new PacketEncoder<>(protocol));
            embedded.pipeline().addLast("unbundler", new PacketBundleUnpacker(protocol.bundlerInfo()));
            embedded.pipeline().addLast("fixture_observer", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    observe(message);
                    context.write(message, promise);
                }
            });
            var listener = new ServerGamePacketListenerImpl(server, wire, handle, CommonListenerCookie.createInitial(profile, false));
            Field packetListener = Connection.class.getDeclaredField("packetListener");
            packetListener.setAccessible(true); packetListener.set(wire, listener);
            player = handle.getBukkitEntity();
            player.setInvulnerable(true);
            registry = server.getPlayerList();
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            registeredPlayers = (List<ServerPlayer>) players.get(registry);
            registry.getPlayersByUUID().put(id, handle); registeredPlayers.add(handle);
            try {
                initial.addNewPlayer(handle);
                player.setViewDistance(3); player.setSendViewDistance(3);
                if (handle.moonrise$getChunkLoader() == null) initial.moonrise$getPlayerChunkLoader().addPlayer(handle);
            } catch (Throwable error) { close(); throw error; }
        }

        private void observe(Object message) {
            if (message instanceof ClientboundPlayerPositionPacket position) teleports.add(position.id());
            if (message instanceof ClientboundChunkBatchFinishedPacket) batchAck = true;
            if (message instanceof ClientboundBlockUpdatePacket block) block(block.getPos(), block.getBlockState());
            if (message instanceof ClientboundSectionBlocksUpdatePacket section) section.runUpdates(this::block);
        }

        private void block(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            if (pos.equals(MARKER) && (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.LAPIS_BLOCK))) markerPackets++;
            if (round != null && round.flood && handle.level() == ((CraftWorld) round.world).getHandle() && state.is(Blocks.WATER)) {
                fakeWater++;
                if (round.world.getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getType() == Material.WATER) realWater = true;
            }
        }

        void pump() {
            embedded.runPendingTasks(); embedded.runScheduledPendingTasks();
            for (int id : List.copyOf(teleports)) handle.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(
                    id, handle.getX(), handle.getY(), handle.getZ(), handle.getYRot(), handle.getXRot()));
            teleports.clear();
            if (batchAck) { batchAck = false; handle.connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(64)); }
            Object message;
            while ((message = embedded.readOutbound()) != null) ReferenceCountUtil.release(message);
            embedded.checkException();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            ServerLevel level = handle.level();
            if (handle.moonrise$getChunkLoader() != null) level.moonrise$getPlayerChunkLoader().removePlayer(handle);
            level.removePlayerImmediately(handle, Entity.RemovalReason.DISCARDED);
            registeredPlayers.remove(handle); registry.getPlayersByUUID().remove(handle.getUUID(), handle);
            // Native removal queues final packets. Drain them while the transport is still open.
            embedded.flushOutbound();
            pump();
            embedded.finishAndReleaseAll(); handle.getTextFilter().leave();
        }
    }
}
