package me.petr1furious.potatominigames.games.pillars;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/** Owns only this match's recordings; the optional recorder API is never shaded. */
public final class PillarsReplays implements Listener, AutoCloseable {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private static final Set<CompletableFuture<Path>> SAVES = ConcurrentHashMap.newKeySet();
    private record Capture(Player player, Path output) {}

    private final Plugin owner;
    private final World world;
    private final Object recording;
    private final Method start;
    private final Method stop;
    private final Path directory;
    private final Map<UUID, Capture> active = new HashMap<>();
    private final Map<UUID, Integer> parts = new HashMap<>();
    private boolean closed;

    static PillarsReplays open(Plugin owner, World world, String modeId) {
        Plugin recorder = owner.getServer().getPluginManager().getPlugin("FlashbackServer");
        if (recorder == null || !recorder.isEnabled()) {
            owner.getLogger().warning("Pillars replay recording unavailable: install FlashbackServer for this Minecraft version.");
            return null;
        }
        try {
            return new PillarsReplays(owner, recorder, world, modeId);
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException exception) {
            owner.getLogger().log(Level.SEVERE, "Cannot initialize Pillars replay recording", cause(exception));
            return null;
        }
    }

    private PillarsReplays(Plugin owner, Plugin recorder, World world, String modeId)
            throws ReflectiveOperationException, java.io.IOException {
        this.owner = owner;
        this.world = world;
        ClassLoader loader = recorder.getClass().getClassLoader();
        Class<?> api = loader.loadClass("dev.zeffut.flashbackserver.api.FlashbackAPI");
        Class<?> service = loader.loadClass("dev.zeffut.flashbackserver.api.RecordingService");
        recording = api.getMethod("recording").invoke(null);
        start = service.getMethod("start", Player.class);
        stop = service.getMethod("stop", Player.class, Path.class);
        Path root = recorder.getDataFolder().toPath().toAbsolutePath().resolve("replays/pillars");
        Files.createDirectories(root);
        // Preset names are user input; neither names nor repeated matches may overwrite a replay.
        directory = Files.createTempDirectory(root, STAMP.format(Instant.now()) + "-"
                + modeId.replaceAll("[^a-zA-Z0-9_-]", "_") + "-");
        owner.getServer().getPluginManager().registerEvents(this, owner);
        owner.getLogger().info("Pillars replays: " + directory);
    }

    void start(Player player) {
        UUID id = player.getUniqueId();
        if (closed || !player.isOnline() || !player.getWorld().equals(world) || active.containsKey(id)) return;
        try {
            if (!Boolean.TRUE.equals(start.invoke(recording, player))) {
                owner.getLogger().warning("Cannot record Pillars POV for " + player.getName()
                        + ": another recording already owns this player");
                return;
            }
            int part = parts.merge(id, 1, Integer::sum);
            active.put(id, new Capture(player, directory.resolve(id + "-part" + part + ".flashback")));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().log(Level.SEVERE, "Cannot start Pillars replay for " + id, cause(exception));
        }
    }

    void stop(Player player) {
        Capture capture = active.remove(player.getUniqueId());
        if (capture != null) save(capture);
    }

    private void save(Capture capture) {
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Path> saved = (CompletableFuture<Path>) stop.invoke(recording, capture.player(), capture.output());
            SAVES.add(saved);
            // Completion is off-thread: no Bukkit world/player access here.
            saved.whenComplete((path, error) -> {
                SAVES.remove(saved);
                if (error != null) owner.getLogger().log(Level.SEVERE,
                        "Cannot save Pillars replay " + capture.output(), error);
                else if (path == null) owner.getLogger().severe("Pillars replay was stopped outside the match: "
                        + capture.output());
                else owner.getLogger().info("Saved Pillars replay: " + path);
            });
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().log(Level.SEVERE, "Cannot stop Pillars replay " + capture.output(), cause(exception));
        }
    }

    // Run before FlashbackServer's NORMAL quit listener, which otherwise saves to its default path.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && !world.equals(event.getTo().getWorld())) stop(event.getPlayer());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(this);
        for (Capture capture : active.values()) save(capture);
        active.clear();
    }

    /** Called after sessions stop, before the recorder plugin and its scheduler are disabled. */
    public static void awaitSaves() {
        CompletableFuture.allOf(SAVES.toArray(CompletableFuture[]::new))
                .exceptionally(error -> null).join(); // Individual save failures are logged above.
    }

    private static Throwable cause(Exception exception) {
        return exception instanceof InvocationTargetException wrapped && wrapped.getCause() != null
                ? wrapped.getCause() : exception;
    }
}
