/*
 * Based on PillarPeril by MarcPG (https://github.com/MarcPG1905/PillarPeril, Apache-2.0),
 * via the fork at https://github.com/Petr1Furious/PillarPeril. Rewritten in Java for
 * Potato Minigames by Claude, and modified; see NOTICE.
 */
package me.petr1furious.potatominigames.games.pillars;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.command.Suggest;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;

import static me.petr1furious.potatominigames.core.command.GameCommands.requirePlayer;
import static me.petr1furious.potatominigames.core.command.GameCommands.run;

/**
 * Pillars ("Pillars of Fortune"): everyone on a bedrock pillar in the sky, a random
 * item every few seconds, last one standing wins. Modes: original, chaos (random
 * layout, fast items) and item-shuffle (the inventory is replaced every cycle).
 */
public final class PillarsGame implements Minigame, Listener {
    private static final List<String> TIME_SUGGESTIONS = List.of("30s", "1min", "2min", "5min", "10min", "30min");

    private final JavaPlugin plugin;
    private final PillarsConfig config;
    private final SlimeWorldService slimeWorlds;
    private PillarsSession activeSession;

    public PillarsGame(JavaPlugin plugin, ConfigRegistry configs, SlimeWorldService slimeWorlds) {
        this.plugin = plugin;
        this.config = new PillarsConfig(configs);
        this.slimeWorlds = slimeWorlds;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    SlimeWorldService slimeWorlds() {
        return slimeWorlds;
    }

    void setActiveSession(PillarsSession session) {
        this.activeSession = session;
    }

    void clearActiveSession(PillarsSession session) {
        if (activeSession == session) {
            activeSession = null;
        }
    }

    @Override
    public String id() {
        return "pillars";
    }

    @Override
    public Component displayName() {
        return Component.text("Pillars", NamedTextColor.LIGHT_PURPLE);
    }

    @Override
    public List<String> modes() {
        return PillarsConfig.MODES;
    }

    @Override
    public String defaultMode() {
        return PillarsConfig.MODES.contains(config.mode.get()) ? config.mode.get() : "original";
    }

    @Override
    public Material icon() {
        return Material.BEDROCK;
    }

    @Override
    public Component description(String mode) {
        return Component.text(switch (mode) {
            case "chaos" -> "Randomized pillar layout, fast item cooldown";
            case "item-shuffle" -> "Items replaced every 10s (9 new items)";
            default -> "Spawn on a pillar, get random items, be the last one standing";
        });
    }

    @Override
    public int minPlayers(String mode) {
        return 2;
    }

    @Override
    public int maxPlayers(String mode) {
        return 16;
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        return new PillarsSession(services, this, config, mode);
    }

    // --- keeping a game's players inside it ---

    private boolean inGame(Player player) {
        return activeSession != null && activeSession.isParticipant(player);
    }

    /** Beds and respawn anchors are items like any other here; they must not move anybody's spawn. */
    @EventHandler(ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        if ((event.getCause() == PlayerSetSpawnEvent.Cause.BED
                || event.getCause() == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR) && inGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (inGame(event.getPlayer())) {
            event.setRespawnLocation(activeSession.respawnPoint());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndPortalFrame(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() == Material.END_PORTAL_FRAME && activeSession != null
                && activeSession.playsIn(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.FIRE && activeSession != null
                && activeSession.playsIn(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalReady(EntityPortalReadyEvent event) {
        if (event.getEntity() instanceof Player player && inGame(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (inGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // --- commands ---

    private PillarsSession requireSession() throws CommandFailure {
        if (activeSession == null) {
            throw new CommandFailure("No pillars game is running.");
        }
        return activeSession;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION);
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("respawn")
                .requires(PillarsGame::isAdmin)
                .executes(ctx -> run(ctx, c -> respawn(c, requirePlayer(c))))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> run(ctx, c -> respawn(c, c.getArgument("player",
                                PlayerSelectorArgumentResolver.class).resolve(c.getSource()).getFirst())))));

        var time = Commands.literal("time").requires(PillarsGame::isAdmin);
        for (String operation : List.of("add", "remove", "set")) {
            time.then(Commands.literal(operation)
                    .then(Commands.argument("time", StringArgumentType.word())
                            .suggests(Suggest.strings(TIME_SUGGESTIONS))
                            .executes(ctx -> run(ctx, c -> changeTime(c, operation)))));
        }
        root.then(time);

        root.then(Commands.literal("info")
                .requires(PillarsGame::isAdmin)
                .executes(ctx -> run(ctx, c -> {
                    for (Component line : requireSession().info()) {
                        c.getSource().getSender().sendMessage(line);
                    }
                })));

        root.then(Commands.literal("arenas")
                .requires(PillarsGame::isAdmin)
                .executes(ctx -> run(ctx, this::listArenas)));

        root.then(Commands.literal("arena")
                .requires(PillarsGame::isAdmin)
                .then(Commands.literal("import")
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(Suggest.dynamic(() -> {
                                    List<String> names = new java.util.ArrayList<>(List.of("all"));
                                    config.arenas().forEach(arena -> names.add(arena.name()));
                                    return names;
                                }))
                                .executes(ctx -> run(ctx, c -> importArenas(c, "pillars", 64)))
                                .then(Commands.argument("world", StringArgumentType.word())
                                        .executes(ctx -> run(ctx, c -> importArenas(c,
                                                c.getArgument("world", String.class), 64)))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 512))
                                                .executes(ctx -> run(ctx, c -> importArenas(c,
                                                        c.getArgument("world", String.class),
                                                        c.getArgument("radius", Integer.class)))))))));
    }

    private void respawn(CommandContext<CommandSourceStack> ctx, Player target) throws CommandFailure {
        if (!requireSession().respawn(target)) {
            throw new CommandFailure("Could not respawn " + target.getName()
                    + ": not in this game, offline, or the game is ending.");
        }
        ctx.getSource().getSender().sendMessage(
                Component.text(target.getName() + " was put back into the game.", NamedTextColor.GREEN));
    }

    private void changeTime(CommandContext<CommandSourceStack> ctx, String operation) throws CommandFailure {
        PillarsSession session = requireSession();
        String raw = ctx.getArgument("time", String.class);
        Long seconds = PillarsConfig.parseDuration(raw);
        if (seconds == null) {
            throw new CommandFailure("Not a time: " + raw + " (try 30s, 2min, 1h)");
        }
        switch (operation) {
            case "add" -> session.addTime(seconds);
            case "remove" -> session.removeTime(seconds);
            default -> session.setTime(seconds);
        }
        ctx.getSource().getSender().sendMessage(Component.text("Time left: "
                + me.petr1furious.potatominigames.core.text.TimeFormat.words(session.timeLeft()), NamedTextColor.GREEN));
    }

    private void listArenas(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PillarsArena> arenas = config.arenas();
        if (arenas.isEmpty()) {
            sender.sendMessage(Component.text("No arenas are configured in games/pillars.yml.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Configured arenas (green = template imported):", NamedTextColor.GRAY));
        for (PillarsArena arena : arenas) {
            boolean ready = slimeWorlds.templateExists(arena.template());
            sender.sendMessage(Component.text()
                    .append(Component.text("| ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(arena.name(), ready ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text(" - " + arena.template() + " @ " + arena.x() + "/" + arena.z()
                            + (arena.time() != null ? ", time " + arena.time() : "")
                            + (arena.gameRules().isEmpty() ? "" : ", rules " + arena.gameRules()),
                            NamedTextColor.GRAY))
                    .build());
        }
    }

    /**
     * Turns arenas built in a vanilla world into their slime templates. A one-time job
     * for the old arena world, and the way to add or rebuild an arena later.
     */
    private void importArenas(CommandContext<CommandSourceStack> ctx, String worldName, int radius)
            throws CommandFailure {
        CommandSender sender = ctx.getSource().getSender();
        String which = ctx.getArgument("arena", String.class);
        List<PillarsArena> arenas = config.arenas().stream()
                .filter(arena -> which.equalsIgnoreCase("all") || arena.name().equalsIgnoreCase(which))
                .toList();
        if (arenas.isEmpty()) {
            throw new CommandFailure("No arena called " + which + " in games/pillars.yml.");
        }
        File source = worldFolder(worldName);
        if (source == null) {
            throw new CommandFailure("Cannot find the world " + worldName + ".");
        }
        int spawnY = (int) Math.floor(config.platformHeight.get()) + 1;
        for (PillarsArena arena : arenas) {
            File work = new File(plugin.getDataFolder(), "import/" + arena.name());
            try {
                ArenaImporter.deleteRecursively(work);
                int chunks = ArenaImporter.cut(source, work, arena.x(), arena.z(), radius);
                if (chunks == 0) {
                    slimeWorlds.createEmptyTemplate(arena.template(), (int) arena.x(), spawnY, (int) arena.z());
                } else {
                    copyLevelData(source, work);
                    slimeWorlds.importVanilla(work, arena.template(), (int) arena.x(), spawnY, (int) arena.z());
                }
                sender.sendMessage(Component.text("Imported " + arena.name() + " as " + arena.template() + " ("
                        + (chunks == 0 ? "empty" : chunks + " chunks") + ")", NamedTextColor.GREEN));
            } catch (Exception exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not import arena " + arena.name(),
                        exception);
                sender.sendMessage(Component.text("Could not import " + arena.name() + ": " + exception,
                        NamedTextColor.RED));
            } finally {
                try {
                    ArenaImporter.deleteRecursively(work);
                } catch (Exception ignored) {
                    // A leftover work folder is harmless and replaced by the next import.
                }
            }
        }
    }

    /** The folder of a world, loaded or not, saving it first when it is loaded. */
    private File worldFolder(String worldName) {
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            loaded.save();
            return loaded.getWorldFolder();
        }
        File container = Bukkit.getWorldContainer();
        for (File candidate : List.of(
                new File(Bukkit.getWorlds().getFirst().getWorldFolder(), "dimensions/minecraft/" + worldName),
                new File(container, worldName))) {
            if (new File(candidate, "region").isDirectory()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * ASP reads a world's level.dat, which a dimension folder of a 1.21.9+ world
     * (world/dimensions/minecraft/<name>) does not have; the one of the world it is part
     * of, a few folders up, is a fine stand-in, as only its version matters.
     */
    private static void copyLevelData(File source, File work) throws java.io.IOException {
        File levelData = null;
        for (File folder = source.getAbsoluteFile(); folder != null && levelData == null;
                folder = folder.getParentFile()) {
            File candidate = new File(folder, "level.dat");
            if (candidate.isFile()) {
                levelData = candidate;
            }
        }
        if (levelData == null) {
            throw new java.io.FileNotFoundException("no level.dat above " + source);
        }
        Files.copy(levelData.toPath(), new File(work, "level.dat").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
