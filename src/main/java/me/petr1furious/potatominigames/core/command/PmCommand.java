package me.petr1furious.potatominigames.core.command;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.queue.QueueService;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.text.Messages;

import static me.petr1furious.potatominigames.core.command.GameCommands.run;
import static me.petr1furious.potatominigames.core.command.GameCommands.requirePlayer;

/**
 * The cross-game admin root: /pm status|games|stop|reload plus the game-agnostic
 * named-kit inventory helpers (save/load/copy).
 */
public final class PmCommand {
    private final JavaPlugin plugin;
    private final SessionManager sessions;
    private final ConfigRegistry configs;
    private final Messages messages;
    private final GameCommands gameCommands;
    private final QueueService queues;
    private final File kitsFile;

    public PmCommand(JavaPlugin plugin, SessionManager sessions, ConfigRegistry configs, Messages messages,
            GameCommands gameCommands, QueueService queues) {
        this.plugin = plugin;
        this.queues = queues;
        this.sessions = sessions;
        this.configs = configs;
        this.messages = messages;
        this.gameCommands = gameCommands;
        this.kitsFile = new File(plugin.getDataFolder(), "data/inventories.yml");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pm")
                .executes(ctx -> run(ctx, this::sendStatus))
                .then(Commands.literal("status").executes(ctx -> run(ctx, this::sendStatus)))
                .then(Commands.literal("games").executes(ctx -> run(ctx, c -> {
                    for (Minigame game : sessions.games()) {
                        c.getSource().getSender().sendMessage(Component.text()
                                .append(Component.text("/" + game.id() + " — ", NamedTextColor.GOLD))
                                .append(game.displayName())
                                .build());
                    }
                })))
                .then(Commands.literal("stop")
                        .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                        .executes(ctx -> run(ctx, c -> {
                            if (sessions.activeSessions().isEmpty()) {
                                throw new CommandFailure("No game is active.");
                            }
                            sessions.stopAll(EndReason.ABORTED);
                            messages.broadcast(Component.text("All games stopped.", NamedTextColor.RED));
                        }))
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(Suggest.dynamic(() -> sessions.activeSessions().stream()
                                        .map(session -> session.game().id()).toList()))
                                .executes(ctx -> run(ctx, c -> {
                                    Minigame game = requireGame(c.getArgument("game", String.class));
                                    var session = sessions.sessionOf(game)
                                            .orElseThrow(() -> new CommandFailure(game.id() + " is not running."));
                                    session.stopNow(EndReason.ABORTED);
                                    messages.broadcast(Component.text().append(session.displayName())
                                            .append(Component.text(" stopped.", NamedTextColor.RED)).build());
                                }))))
                .then(buildForceStart())
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                        .executes(ctx -> run(ctx, c -> {
                            configs.reloadAll();
                            c.getSource().getSender().sendMessage(messages.success("Configuration reloaded."));
                        })))
                .then(buildInventorySubtree())
                .then(buildConfigSubtree())
                .then(gameCommands.buildGiveSubtree())
                .build();
    }

    private void sendStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var running = sessions.activeSessions();
        if (running.isEmpty()) {
            sender.sendMessage(Component.text(
                    "PotatoMinigames " + plugin.getPluginMeta().getVersion() + " — no game running.",
                    NamedTextColor.GRAY));
        }
        for (var session : running) {
            sender.sendMessage(Component.text()
                    .append(Component.text("PotatoMinigames: ", NamedTextColor.GRAY))
                    .append(session.displayName())
                    .append(Component.text(" is " + session.phase().name().toLowerCase() + " with "
                            + Messages.count(session.participants().size(), "player") + " and "
                            + Messages.count(session.spectatorIds().size(), "spectator") + ".", NamedTextColor.GRAY))
                    .build());
        }
        for (var queue : queues.visibleQueues()) {
            if (queue.size() > 0) {
                sender.sendMessage(Component.text()
                        .append(Component.text("Queue ", NamedTextColor.GRAY))
                        .append(queue.displayName())
                        .append(Component.text(": " + queue.countText()
                                + (queue.countdown() != null ? ", starts in " + queue.countdown() + "s" : ""),
                                NamedTextColor.GRAY))
                        .build());
            }
        }
    }

    /** /pm forcestart <game> [mode]: skip the queue's minimum and timers and start now. */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildForceStart() {
        return Commands.literal("forcestart")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.argument("game", StringArgumentType.word())
                        .suggests(Suggest.dynamic(() -> sessions.games().stream().map(Minigame::id).toList()))
                        .executes(ctx -> run(ctx, c -> forceStart(c, null)))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Minigame game = sessions.game(context.getArgument("game", String.class));
                                    return Suggest.strings(game == null ? List.of() : game.modes())
                                            .getSuggestions(context, builder);
                                })
                                .executes(ctx -> run(ctx, c -> forceStart(c, c.getArgument("mode", String.class))))));
    }

    private void forceStart(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String mode)
            throws CommandFailure {
        Minigame game = requireGame(ctx.getArgument("game", String.class));
        if (mode == null) {
            // Without a mode, the fullest queue of that game is the one people are waiting in.
            var fullest = queues.queuesOf(game).stream()
                    .max(java.util.Comparator.comparingInt(QueueService.Queue::size));
            if (fullest.isEmpty()) {
                throw new CommandFailure(game.id() + " has no open queues.");
            }
            queues.forceStart(fullest.get());
            return;
        }
        var queue = queues.find(game, mode)
                .orElseThrow(() -> new CommandFailure("Unknown mode for " + game.id() + ": " + mode));
        queues.forceStart(queue);
    }

    private Minigame requireGame(String id) throws CommandFailure {
        Minigame game = sessions.game(id);
        if (game == null) {
            throw new CommandFailure("Unknown game: " + id);
        }
        return game;
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildInventorySubtree() {
        return Commands.literal("inventory")
                .requires(source -> source.getSender().hasPermission(GameCommands.ADMIN_PERMISSION))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> run(ctx, c -> {
                                    Player player = requirePlayer(c);
                                    saveKit(c.getArgument("name", String.class),
                                            player.getInventory().getContents());
                                    player.sendMessage(messages.success("Inventory saved as '"
                                            + c.getArgument("name", String.class) + "'."));
                                }))))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(Suggest.dynamic(this::kitNames))
                                .executes(ctx -> run(ctx, c -> {
                                    Player player = requirePlayer(c);
                                    loadKit(c.getArgument("name", String.class), List.of(player));
                                    player.sendMessage(messages.success("Inventory loaded."));
                                }))
                                .then(Commands.argument("targets", ArgumentTypes.players())
                                        .executes(ctx -> run(ctx, c -> {
                                            var targets = c
                                                    .getArgument("targets", PlayerSelectorArgumentResolver.class)
                                                    .resolve(c.getSource());
                                            loadKit(c.getArgument("name", String.class), targets);
                                            c.getSource().getSender().sendMessage(messages.success(
                                                    "Inventory loaded for " + targets.size() + " players."));
                                        })))))
                .then(Commands.literal("copy")
                        .then(Commands.argument("source", ArgumentTypes.player())
                                .then(Commands.argument("targets", ArgumentTypes.players())
                                        .executes(ctx -> run(ctx, c -> {
                                            Player source = c
                                                    .getArgument("source", PlayerSelectorArgumentResolver.class)
                                                    .resolve(c.getSource()).getFirst();
                                            var targets = c
                                                    .getArgument("targets", PlayerSelectorArgumentResolver.class)
                                                    .resolve(c.getSource());
                                            for (Player target : targets) {
                                                target.getInventory()
                                                        .setContents(source.getInventory().getContents());
                                            }
                                            c.getSource().getSender()
                                                    .sendMessage(messages.success("Copied inventory."));
                                        })))));
    }

    /** /pm config covers every registered section, including "core" and "arena.asp". */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildConfigSubtree() {
        return Commands.literal("config")
                .executes(ctx -> run(ctx, c -> {
                    for (String bagId : configs.bagIds()) {
                        c.getSource().getSender().sendMessage(Component.text()
                                .append(Component.text(bagId + ": ", NamedTextColor.GOLD))
                                .append(Component.text(String.join(", ", configs.bag(bagId).names()),
                                        NamedTextColor.GRAY))
                                .build());
                    }
                }))
                .then(Commands.argument("section", StringArgumentType.word())
                        .suggests(Suggest.dynamic(configs::bagIds))
                        .then(Commands.literal("get")
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests((context, builder) -> Suggest.strings(propertyNames(
                                                context.getArgument("section", String.class)))
                                                .getSuggestions(context, builder))
                                        .executes(ctx -> run(ctx, c -> {
                                            var bag = requireBag(c.getArgument("section", String.class));
                                            String property = c.getArgument("property", String.class);
                                            String value = bag.render(property);
                                            if (value == null) {
                                                throw new CommandFailure("Unknown property: " + property);
                                            }
                                            c.getSource().getSender().sendMessage(Component.text(
                                                    bag.id() + "." + property + " = " + value,
                                                    NamedTextColor.GRAY));
                                        }))))
                        .then(Commands.literal("set")
                                .requires(source -> source.getSender()
                                        .hasPermission(GameCommands.ADMIN_PERMISSION))
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests((context, builder) -> Suggest.strings(propertyNames(
                                                context.getArgument("section", String.class)))
                                                .getSuggestions(context, builder))
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> run(ctx, c -> {
                                                    var bag = requireBag(
                                                            c.getArgument("section", String.class));
                                                    String property = c.getArgument("property", String.class);
                                                    String value = c.getArgument("value", String.class);
                                                    switch (configs.set(bag.id(), property, value)) {
                                                        case SUCCESS -> c.getSource().getSender().sendMessage(
                                                                messages.success(bag.id() + "." + property
                                                                        + " = " + bag.render(property)));
                                                        case UNKNOWN_PROPERTY -> throw new CommandFailure(
                                                                "Unknown property: " + property);
                                                        case PARSE_ERROR -> throw new CommandFailure(
                                                                "Invalid value for " + property + ": " + value);
                                                    }
                                                }))))));
    }

    private List<String> propertyNames(String bagId) {
        var bag = configs.bag(bagId);
        return bag == null ? List.of() : bag.names();
    }

    private me.petr1furious.potatominigames.core.config.PropertyBag requireBag(String section)
            throws CommandFailure {
        var bag = configs.bag(section);
        if (bag == null) {
            throw new CommandFailure("Unknown config section: " + section);
        }
        return bag;
    }

    private List<String> kitNames() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(kitsFile);
        var section = config.getConfigurationSection("kits");
        return section == null ? List.of() : List.copyOf(section.getKeys(false));
    }

    private void saveKit(String name, ItemStack[] contents) throws CommandFailure {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(kitsFile);
        config.set("kits." + name, Arrays.asList(contents));
        try {
            kitsFile.getParentFile().mkdirs();
            config.save(kitsFile);
        } catch (IOException exception) {
            throw new CommandFailure("Failed to save inventory: " + exception.getMessage());
        }
    }

    private void loadKit(String name, List<Player> targets) throws CommandFailure {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(kitsFile);
        List<?> stored = config.getList("kits." + name);
        if (stored == null) {
            throw new CommandFailure("No saved inventory named '" + name + "'.");
        }
        ItemStack[] contents = stored.stream()
                .map(item -> item instanceof ItemStack stack ? stack : null)
                .toArray(ItemStack[]::new);
        for (Player target : targets) {
            target.getInventory().setContents(contents);
        }
    }
}
