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
import me.petr1furious.potatominigames.core.session.GamePhase;
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
    private final File kitsFile;

    public PmCommand(JavaPlugin plugin, SessionManager sessions, ConfigRegistry configs, Messages messages,
            GameCommands gameCommands) {
        this.plugin = plugin;
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
                            if (sessions.phase() == GamePhase.IDLE) {
                                throw new CommandFailure("No game is active.");
                            }
                            sessions.stopNow(EndReason.ABORTED);
                            messages.broadcast(Component.text("Game stopped.", NamedTextColor.RED));
                        })))
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
        if (sessions.phase() == GamePhase.IDLE) {
            sender.sendMessage(Component.text(
                    "PotatoMinigames " + plugin.getPluginMeta().getVersion() + " — no game running.",
                    NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text()
                .append(Component.text("PotatoMinigames: ", NamedTextColor.GRAY))
                .append(sessions.activeGame().map(Minigame::displayName).orElse(Component.text("?")))
                .append(Component.text(" is " + sessions.phase().name().toLowerCase() + " with "
                        + sessions.participants().size() + " players.", NamedTextColor.GRAY))
                .build());
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
