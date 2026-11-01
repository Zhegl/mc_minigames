package me.petr1furious.potatominigames.core.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.ConfigEditor;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.StartOption;
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

        root.then(buildStart("start", game, false));
        root.then(buildStart("restart", game, true));
        root.then(buildTest(game));

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

        // Edits only the game's own file.
        String configFile = configs.fileOf(game.id());
        if (configFile != null) {
            root.then(new ConfigCommand(game.id(), new ConfigEditor(configs).mount("", configFile)).build());
        }

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

    /** {@code start [mode] [players] [option]}; mode suggestions remain live for player presets. */
    private LiteralArgumentBuilder<CommandSourceStack> buildStart(String name, Minigame game, boolean restart) {
        LiteralArgumentBuilder<CommandSourceStack> start = Commands.literal(name).requires(GameCommands::isAdmin);
        if (game.showModes()) {
            var mode = Commands.argument("mode", modeArgument(game)).suggests(Suggest.dynamic(game::modes));
            addStartTail(mode, game, true, restart);
            start.then(mode);
        }
        addStartTail(start, game, false, restart);
        return start;
    }

    private void addStartTail(ArgumentBuilder<CommandSourceStack, ?> node, Minigame game, boolean modeArgument,
            boolean restart) {
        node.executes(ctx -> run(ctx, c -> launch(c, game,
                modeArgument ? c.getArgument("mode", String.class) : null, false, null, restart)));
        var players = Commands.argument("players", ArgumentTypes.players())
                .executes(ctx -> run(ctx, c -> launch(c, game,
                        modeArgument ? c.getArgument("mode", String.class) : null, true, null, restart)));
        StartOption option = game.startOption();
        if (option != null) {
            players.then(Commands.argument(option.key(), StringArgumentType.word())
                    .suggests(Suggest.dynamic(() -> option.values().get()))
                    .executes(ctx -> run(ctx, c -> launch(c, game,
                            modeArgument ? c.getArgument("mode", String.class) : null, true,
                            c.getArgument(option.key(), String.class), restart))));
        }
        node.then(players);
    }

    /**
     * {@code test [mode] [option]}: the sender alone, as a test game (no points, no early end for
     * the last one standing; see {@link ActiveSession#isTest}).
     */
    private LiteralArgumentBuilder<CommandSourceStack> buildTest(Minigame game) {
        LiteralArgumentBuilder<CommandSourceStack> test = Commands.literal("test").requires(GameCommands::isAdmin);
        if (game.showModes()) {
            var mode = Commands.argument("mode", modeArgument(game)).suggests(Suggest.dynamic(game::modes));
            addTestTail(mode, game, true);
            test.then(mode);
        }
        addTestTail(test, game, false);
        return test;
    }

    private void addTestTail(ArgumentBuilder<CommandSourceStack, ?> node, Minigame game, boolean modeArgument) {
        node.executes(ctx -> run(ctx, c -> launchTest(c, game,
                modeArgument ? c.getArgument("mode", String.class) : null, null)));
        StartOption option = game.startOption();
        if (option != null) {
            node.then(Commands.argument(option.key(), StringArgumentType.word())
                    .suggests(Suggest.dynamic(() -> option.values().get()))
                    .executes(ctx -> run(ctx, c -> launchTest(c, game,
                            modeArgument ? c.getArgument("mode", String.class) : null,
                            c.getArgument(option.key(), String.class)))));
        }
    }

    private void launchTest(CommandContext<CommandSourceStack> ctx, Minigame game, String mode, String optionValue)
            throws CommandFailure {
        requireKnownMode(game, mode);
        Player player = requirePlayer(ctx);
        Map<String, String> options = new HashMap<>();
        options.put(ActiveSession.TEST_OPTION, "true");
        if (optionValue != null) {
            StartOption option = game.startOption();
            options.put(option.key(), requireOptionValue(option, optionValue));
        }
        sessions.start(game, mode != null ? mode : game.defaultMode(), List.of(player), Map.of(), false, options);
    }

    /** @param mode null for the default, or the running mode on restart */
    private void launch(CommandContext<CommandSourceStack> ctx, Minigame game, String mode, boolean selected,
            String optionValue, boolean restart) throws CommandFailure, CommandSyntaxException {
        requireKnownMode(game, mode);
        List<Player> roster = selected ? resolveRoster(ctx, game, mode == null) : null;
        Map<String, String> options = new HashMap<>();
        if (optionValue != null) {
            StartOption option = game.startOption();
            options.put(option.key(), requireOptionValue(option, optionValue));
        }
        var running = sessions.sessionOf(game);
        if (restart && running.isPresent()) {
            if (roster == null || roster.isEmpty()) {
                roster = running.get().participants().onlinePlayers();
            }
            if (mode == null) {
                mode = running.get().mode();
            }
            if (options.isEmpty()) {
                options.putAll(running.get().options());
            }
            running.get().stopNow(EndReason.ABORTED);
        }
        start(game, mode != null ? mode : game.defaultMode(), roster, options);
    }

    /** A selector matching nobody fails; a bare word is usually a mistyped mode or a missing selector. */
    private static List<Player> resolveRoster(CommandContext<CommandSourceStack> ctx, Minigame game,
            boolean firstArgument) throws CommandFailure, CommandSyntaxException {
        List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        if (!players.isEmpty()) {
            return players;
        }
        String raw = ctx.getNodes().stream()
                .filter(node -> node.getNode().getName().equals("players"))
                .findFirst().map(node -> node.getRange().get(ctx.getInput())).orElse("@");
        if (raw.startsWith("@")) {
            throw new CommandFailure("No players match " + raw + ".");
        }
        StartOption option = game.startOption();
        if (option != null && option.values().get().stream().anyMatch(raw::equalsIgnoreCase)) {
            throw new CommandFailure("Give players before the " + option.key() + " " + raw + ": " + usage(game));
        }
        if (firstArgument && game.showModes()) {
            throw new CommandFailure("Unknown mode or player: " + raw + " (modes: "
                    + String.join(", ", game.modes()) + ")");
        }
        throw new CommandFailure("No player called " + raw + " is online.");
    }

    /** Reject non-modes during parsing, so the sibling player/arena argument can still match. */
    private static ArgumentType<String> modeArgument(Minigame game) {
        return new CustomArgumentType.Converted<String, String>() {
            @Override
            public String convert(String value) throws CommandSyntaxException {
                if (!game.modes().contains(value)) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
                }
                return value;
            }

            @Override
            public ArgumentType<String> getNativeType() {
                return StringArgumentType.word();
            }
        };
    }

    private static void requireKnownMode(Minigame game, String mode) throws CommandFailure {
        if (mode != null && !game.modes().contains(mode)) {
            throw new CommandFailure("Unknown mode: " + mode + " (modes: " + String.join(", ", game.modes()) + ")");
        }
    }

    private static String requireOptionValue(StartOption option, String raw) throws CommandFailure {
        List<String> values = option.values().get();
        for (String value : values) {
            if (value.equalsIgnoreCase(raw)) {
                return value;
            }
        }
        throw new CommandFailure("Unknown " + option.key() + ": " + raw + " ("
                + (values.isEmpty() ? "none available" : "options: " + String.join(", ", values)) + ")");
    }

    private static String usage(Minigame game) {
        StartOption option = game.startOption();
        return "/" + game.id() + " start" + (game.showModes() ? " [mode]" : "") + " [players]"
                + (option != null ? " [" + option.key() + "]" : "");
    }

    /** Defaults to everyone online not already in a game. */
    private void start(Minigame game, String mode, List<Player> selected, Map<String, String> options)
            throws CommandFailure {
        List<Player> roster = selected;
        if (roster == null || roster.isEmpty()) {
            roster = new ArrayList<>();
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!sessions.isPlaying(player)) {
                    roster.add(player);
                }
            }
        }
        sessions.start(game, mode, roster, Map.of(), false, options);
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
            if (isAdmin(ctx.getSource())) {
                sender.sendMessage(Component.text()
                        .append(Component.text("Start now: ", NamedTextColor.GRAY))
                        .append(Component.text(usage(game), NamedTextColor.GOLD))
                        .append(Component.text(game.showModes()
                                ? " (modes: " + String.join(", ", game.modes()) + ")" : "", NamedTextColor.GRAY))
                        .build());
            }
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
}
