package me.petr1furious.potatominigames.core.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.score.PointsService;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;

/** Each game's admin command root: the shared subtree plus {@link Minigame#extendCommands}. */
public final class GameCommands {
    public static final String ADMIN_PERMISSION = "potatominigames.admin";

    private final SessionManager sessions;
    private final TeamService teams;
    private final PointsService points;
    private final ConfigRegistry configs;
    private final Messages messages;
    private final me.petr1furious.potatominigames.gadgets.GadgetRegistry gadgets;

    public GameCommands(SessionManager sessions, TeamService teams, PointsService points,
            ConfigRegistry configs, Messages messages,
            me.petr1furious.potatominigames.gadgets.GadgetRegistry gadgets) {
        this.sessions = sessions;
        this.teams = teams;
        this.points = points;
        this.configs = configs;
        this.messages = messages;
        this.gadgets = gadgets;
    }

    @FunctionalInterface
    public interface Action {
        void run(CommandContext<CommandSourceStack> ctx) throws CommandFailure, CommandSyntaxException;
    }

    /** Renders {@link CommandFailure} as a red message. */
    public static int run(CommandContext<CommandSourceStack> ctx, Action action) {
        try {
            action.run(ctx);
            return Command.SINGLE_SUCCESS;
        } catch (CommandFailure failure) {
            ctx.getSource().getSender().sendMessage(failure.text());
            return 0;
        } catch (CommandSyntaxException exception) {
            ctx.getSource().getSender().sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    public static Player requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandFailure {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        throw new CommandFailure("This command can only be used by a player.");
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.getSender().hasPermission(ADMIN_PERMISSION);
    }

    public LiteralCommandNode<CommandSourceStack> buildGameRoot(Minigame game) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(game.id());

        root.executes(ctx -> run(ctx, c -> sendStatus(c, game)));

        root.then(Commands.literal("start")
                .requires(GameCommands::isAdmin)
                .executes(ctx -> run(ctx, c -> start(game, null)))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> run(ctx, c -> start(game,
                                c.getArgument("players", PlayerSelectorArgumentResolver.class)
                                        .resolve(c.getSource()))))));

        root.then(Commands.literal("restart")
                .requires(GameCommands::isAdmin)
                .executes(ctx -> run(ctx, c -> restart(game, null)))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> run(ctx, c -> restart(game,
                                c.getArgument("players", PlayerSelectorArgumentResolver.class)
                                        .resolve(c.getSource()))))));

        root.then(Commands.literal("stop")
                .requires(GameCommands::isAdmin)
                .executes(ctx -> run(ctx, c -> {
                    ActiveSession session = requireOwnSession(game);
                    session.stopNow(EndReason.ABORTED);
                    messages.broadcast(Component.text().append(session.displayName())
                            .append(Component.text(" stopped.", NamedTextColor.RED)).build());
                })));

        // Admin-only; players spectate through /play.
        root.then(Commands.literal("join")
                .requires(GameCommands::isAdmin)
                .executes(ctx -> run(ctx, c -> requireOwnSession(game).join(requirePlayer(c))))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> run(ctx, c -> {
                            ActiveSession session = requireOwnSession(game);
                            for (Player target : c.getArgument("players", PlayerSelectorArgumentResolver.class)
                                    .resolve(c.getSource())) {
                                session.join(target);
                            }
                        }))));

        root.then(Commands.literal("leave")
                .executes(ctx -> run(ctx, c -> requireOwnSession(game).leave(requirePlayer(c))))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(GameCommands::isAdmin)
                        .executes(ctx -> run(ctx, c -> {
                            ActiveSession session = requireOwnSession(game);
                            for (Player target : c.getArgument("players", PlayerSelectorArgumentResolver.class)
                                    .resolve(c.getSource())) {
                                session.leave(target);
                            }
                        }))));

        root.then(Commands.literal("list")
                .executes(ctx -> run(ctx, c -> sendList(c, game))));

        root.then(Commands.literal("points")
                .executes(ctx -> run(ctx, c -> sendPoints(c, game)))
                .then(Commands.literal("reset")
                        .requires(GameCommands::isAdmin)
                        .executes(ctx -> run(ctx, c -> {
                            points.reset(game.id());
                            c.getSource().getSender().sendMessage(
                                    messages.success("Points for " + game.id() + " reset."));
                        }))));

        if (game.usesTeams()) {
            root.then(buildTeamSubtree(game));
        }

        if (game.usesGadgets()) {
            root.then(buildGiveSubtree());
        }

        root.then(buildConfigSubtree(game));

        root.then(Commands.literal("reload")
                .requires(GameCommands::isAdmin)
                .executes(ctx -> run(ctx, c -> {
                    configs.reloadAll();
                    teams.reload();
                    c.getSource().getSender().sendMessage(messages.success("Configuration reloaded."));
                })));

        game.extendCommands(root);
        return root.build();
    }

    private ActiveSession requireOwnSession(Minigame game) throws CommandFailure {
        return sessions.sessionOf(game)
                .orElseThrow(() -> new CommandFailure("No " + game.id() + " game is active."));
    }

    /** Defaults to everyone online not already in a game. */
    private void start(Minigame game, List<Player> selected) throws CommandFailure {
        List<Player> roster = selected;
        if (roster == null || roster.isEmpty()) {
            roster = new ArrayList<>();
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!sessions.isPlaying(player)) {
                    roster.add(player);
                }
            }
        }
        sessions.start(game, game.defaultMode(), roster, java.util.Map.of(), false);
    }

    /** Ends and restarts with the same players unless a selector is given; a plain start when idle. */
    private void restart(Minigame game, List<Player> selected) throws CommandFailure {
        List<Player> roster = selected;
        var running = sessions.sessionOf(game);
        if (running.isPresent()) {
            if (roster == null || roster.isEmpty()) {
                roster = running.get().participants().onlinePlayers();
            }
            running.get().stopNow(EndReason.ABORTED);
        }
        start(game, roster);
    }

    private void sendStatus(CommandContext<CommandSourceStack> ctx, Minigame game) {
        var sender = ctx.getSource().getSender();
        var running = sessions.sessionOf(game);
        if (running.isEmpty()) {
            sender.sendMessage(Component.text()
                    .append(game.displayName())
                    .append(Component.text(" — not running. Queue with ", NamedTextColor.GRAY))
                    .append(Component.text(sessions.playCommand(game, game.defaultMode()), NamedTextColor.GOLD))
                    .build());
            return;
        }
        ActiveSession session = running.get();
        sender.sendMessage(Component.text()
                .append(session.displayName())
                .append(Component.text(" — " + session.phase().name().toLowerCase() + ", "
                        + Messages.count(session.participants().size(), "player") + ", "
                        + Messages.count(session.spectatorIds().size(), "spectator"), NamedTextColor.GRAY))
                .build());
    }

    private void sendList(CommandContext<CommandSourceStack> ctx, Minigame game) throws CommandFailure {
        var running = sessions.sessionOf(game);
        // No game running: show saved colours.
        if (running.isEmpty()) {
            if (game.usesTeams()) {
                sendStandingTeams(ctx, game);
            } else {
                ctx.getSource().getSender().sendMessage(
                        messages.info("No " + game.id() + " game is running."));
            }
            return;
        }
        List<Component> names = new ArrayList<>();
        for (Participant participant : running.get().participants().all()) {
            NamedTextColor color = participant.team() != null ? participant.team().textColor()
                    : NamedTextColor.WHITE;
            Component name = Component.text(participant.name(), color);
            if (!participant.isOnline()) {
                name = name.append(Component.text(" (offline)", NamedTextColor.DARK_GRAY));
            }
            names.add(name);
        }
        ctx.getSource().getSender().sendMessage(Component.text()
                .append(Component.text("Players (" + names.size() + "): ", NamedTextColor.GRAY))
                .append(Component.join(JoinConfiguration.commas(true), names))
                .build());
    }

    private void sendPoints(CommandContext<CommandSourceStack> ctx, Minigame game) {
        var sender = ctx.getSource().getSender();
        List<PointsService.Entry> standings = points.standings(game.id());
        if (standings.isEmpty()) {
            sender.sendMessage(messages.info("No points recorded for " + game.id() + " yet."));
            return;
        }
        sender.sendMessage(Component.text()
                .append(game.displayName())
                .append(Component.text(" standings:", NamedTextColor.GRAY))
                .build());
        int place = 1;
        for (PointsService.Entry entry : standings) {
            sender.sendMessage(Component.text()
                    .append(Component.text(place + ". ", NamedTextColor.GOLD))
                    .append(Component.text(entry.name(), NamedTextColor.AQUA))
                    .append(Component.text(" — " + entry.points(), NamedTextColor.GREEN))
                    .build());
            place++;
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildTeamSubtree(Minigame game) {
        var teamColors = java.util.Arrays.stream(TeamColor.values()).map(TeamColor::id).toList();
        return Commands.literal("team")
                .then(Commands.literal("list")
                        .executes(ctx -> run(ctx, c -> sendTeamList(c, game))))
                .then(Commands.literal("join")
                        .requires(GameCommands::isAdmin)
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests(Suggest.strings(teamColors))
                                .executes(ctx -> run(ctx, c -> teamJoin(game, requirePlayer(c),
                                        c.getArgument("color", String.class), c)))
                                .then(Commands.argument("players", ArgumentTypes.players())
                                        .requires(GameCommands::isAdmin)
                                        .executes(ctx -> run(ctx, c -> {
                                            for (Player target : c
                                                    .getArgument("players", PlayerSelectorArgumentResolver.class)
                                                    .resolve(c.getSource())) {
                                                teamJoin(game, target, c.getArgument("color", String.class), c);
                                            }
                                        })))))
                .then(Commands.literal("leave")
                        .requires(GameCommands::isAdmin)
                        .executes(ctx -> run(ctx, c -> teamLeave(game, requirePlayer(c))))
                        .then(Commands.argument("players", ArgumentTypes.players())
                                .requires(GameCommands::isAdmin)
                                .executes(ctx -> run(ctx, c -> {
                                    for (Player target : c
                                            .getArgument("players", PlayerSelectorArgumentResolver.class)
                                            .resolve(c.getSource())) {
                                        teamLeave(game, target);
                                    }
                                }))))
                .then(Commands.literal("reset")
                        .requires(GameCommands::isAdmin)
                        .executes(ctx -> run(ctx, c -> {
                            teams.resetStanding(game.id());
                            c.getSource().getSender().sendMessage(
                                    messages.success("Saved " + game.id() + " team colours cleared."));
                        })))
                .then(Commands.literal("cycle")
                        .requires(GameCommands::isAdmin)
                        .executes(ctx -> run(ctx, c -> {
                            Player player = requirePlayer(c);
                            TeamColor next = teams.cycle(game.id(), player.getUniqueId());
                            applyTeamChange(game, player, next);
                            c.getSource().getSender().sendMessage(Component.text()
                                    .append(Component.text("You are now on ", NamedTextColor.GREEN))
                                    .append(next.displayNameComponent())
                                    .build());
                        })))
                .then(Commands.literal("swap")
                        .requires(GameCommands::isAdmin)
                        .then(Commands.argument("first", ArgumentTypes.player())
                                .then(Commands.argument("second", ArgumentTypes.player())
                                        .executes(ctx -> run(ctx, c -> {
                                            Player first = c
                                                    .getArgument("first", PlayerSelectorArgumentResolver.class)
                                                    .resolve(c.getSource()).getFirst();
                                            Player second = c
                                                    .getArgument("second", PlayerSelectorArgumentResolver.class)
                                                    .resolve(c.getSource()).getFirst();
                                            teams.swap(game.id(), first.getUniqueId(), second.getUniqueId());
                                            applyTeamChange(game, first,
                                                    teams.standingTeam(game.id(), first.getUniqueId()));
                                            applyTeamChange(game, second,
                                                    teams.standingTeam(game.id(), second.getUniqueId()));
                                            c.getSource().getSender()
                                                    .sendMessage(messages.success("Swapped teams."));
                                        })))));
    }

    /** Saved colours for admin-started games; queued games use /play team picks. */
    private void teamJoin(Minigame game, Player player, String colorId,
            CommandContext<CommandSourceStack> ctx) throws CommandFailure {
        TeamColor color = TeamColor.fromId(colorId);
        if (color == null) {
            throw new CommandFailure("Unknown team color: " + colorId);
        }
        teams.assign(game.id(), player.getUniqueId(), color);
        applyTeamChange(game, player, color);
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" joined ", NamedTextColor.GREEN))
                .append(color.displayNameComponent())
                .append(Component.text(" team for ", NamedTextColor.GREEN))
                .append(game.displayName())
                .build());
    }

    private void teamLeave(Minigame game, Player player) {
        teams.unassign(game.id(), player.getUniqueId());
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" left their ", NamedTextColor.YELLOW))
                .append(game.displayName())
                .append(Component.text(" team", NamedTextColor.YELLOW))
                .build());
    }

    private void applyTeamChange(Minigame game, Player player, TeamColor color) {
        sessions.sessionOf(game).ifPresent(session -> {
            session.participants().find(player.getUniqueId()).ifPresent(participant -> participant.setTeam(color));
            session.gameSession().onTeamChange(player, color);
        });
    }

    private void sendTeamList(CommandContext<CommandSourceStack> ctx, Minigame game) {
        var running = sessions.sessionOf(game);
        if (running.isPresent()) {
            var sender = ctx.getSource().getSender();
            var byTeam = running.get().teams().byTeam();
            if (byTeam.isEmpty()) {
                sender.sendMessage(messages.info("No teams assigned yet."));
                return;
            }
            for (var entry : byTeam.entrySet()) {
                List<Component> names = entry.getValue().stream()
                        .map(participant -> Component.text(participant.name(), NamedTextColor.WHITE))
                        .collect(java.util.stream.Collectors.toList());
                sender.sendMessage(Component.text()
                        .append(entry.getKey().displayNameComponent())
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(Component.join(JoinConfiguration.commas(true), names))
                        .build());
            }
            return;
        }
        sendStandingTeams(ctx, game);
    }

    private void sendStandingTeams(CommandContext<CommandSourceStack> ctx, Minigame game) {
        var sender = ctx.getSource().getSender();
        var byTeam = teams.standingByTeam(game.id());
        if (byTeam.isEmpty()) {
            sender.sendMessage(messages.info("No teams set for " + game.id() + " — use /"
                    + game.id() + " team join <color>."));
            return;
        }
        var names = teams.nameLookup(teams.standingTeams(game.id()).keySet());
        for (var entry : byTeam.entrySet()) {
            List<Component> members = entry.getValue().stream()
                    .map(id -> Component.text(names.get(id), NamedTextColor.WHITE))
                    .collect(java.util.stream.Collectors.toList());
            sender.sendMessage(Component.text()
                    .append(entry.getKey().displayNameComponent())
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.join(JoinConfiguration.commas(true), members))
                    .build());
        }
    }

    private List<String> bagIdsFor(Minigame game) {
        List<String> ids = new ArrayList<>();
        for (String id : configs.bagIds()) {
            if (id.equals(game.id()) || id.startsWith(game.id() + ".")
                    || (game.usesGadgets() && id.startsWith("gadgets"))) {
                ids.add(id);
            }
        }
        return ids;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildConfigSubtree(Minigame game) {
        return Commands.literal("config")
                .executes(ctx -> run(ctx, c -> {
                    var sender = c.getSource().getSender();
                    for (String bagId : bagIdsFor(game)) {
                        PropertyBag bag = configs.bag(bagId);
                        sender.sendMessage(Component.text()
                                .append(Component.text(bagId + ": ", NamedTextColor.GOLD))
                                .append(Component.text(String.join(", ", bag.names()), NamedTextColor.GRAY))
                                .build());
                    }
                }))
                .then(Commands.argument("section", StringArgumentType.word())
                        .suggests(Suggest.dynamic(() -> new ArrayList<>(bagIdsFor(game))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests((context, builder) -> Suggest
                                                .strings(propertyNames(context.getArgument("section", String.class)))
                                                .getSuggestions(context, builder))
                                        .executes(ctx -> run(ctx, c -> {
                                            PropertyBag bag = requireBag(game,
                                                    c.getArgument("section", String.class));
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
                                .requires(GameCommands::isAdmin)
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests((context, builder) -> Suggest
                                                .strings(propertyNames(context.getArgument("section", String.class)))
                                                .getSuggestions(context, builder))
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> run(ctx, c -> {
                                                    String section = c.getArgument("section", String.class);
                                                    PropertyBag bag = requireBag(game, section);
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

    /** Also mounted under /pm. */
    public LiteralArgumentBuilder<CommandSourceStack> buildGiveSubtree() {
        return Commands.literal("give")
                .requires(GameCommands::isAdmin)
                .then(Commands.argument("gadget", StringArgumentType.word())
                        .suggests(Suggest.dynamic(gadgets::ids))
                        .executes(ctx -> run(ctx, c -> giveGadget(c, List.of(requirePlayer(c)), 1)))
                        .then(Commands.argument("players", ArgumentTypes.players())
                                .executes(ctx -> run(ctx, c -> giveGadget(c,
                                        c.getArgument("players", PlayerSelectorArgumentResolver.class)
                                                .resolve(c.getSource()),
                                        1)))
                                .then(Commands.argument("count",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> run(ctx, c -> giveGadget(c,
                                                c.getArgument("players", PlayerSelectorArgumentResolver.class)
                                                        .resolve(c.getSource()),
                                                c.getArgument("count", Integer.class)))))));
    }

    private void giveGadget(CommandContext<CommandSourceStack> ctx, List<Player> targets, int count)
            throws CommandFailure {
        String gadgetId = ctx.getArgument("gadget", String.class);
        var gadget = gadgets.get(gadgetId);
        if (gadget == null) {
            throw new CommandFailure("Unknown gadget: " + gadgetId);
        }
        for (Player target : targets) {
            target.getInventory().addItem(gadget.createItem(count));
        }
        ctx.getSource().getSender().sendMessage(
                messages.success("Gave " + gadgetId + " x" + count + " to " + targets.size() + " player(s)."));
    }

    private List<String> propertyNames(String bagId) {
        PropertyBag bag = bagId == null ? null : configs.bag(bagId);
        return bag == null ? List.of() : bag.names();
    }

    private PropertyBag requireBag(Minigame game, String section) throws CommandFailure {
        if (!bagIdsFor(game).contains(section)) {
            throw new CommandFailure("Unknown config section: " + section);
        }
        return configs.bag(section);
    }
}
