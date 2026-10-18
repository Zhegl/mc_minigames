package me.petr1furious.potatominigames.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.queue.QueueMenu;
import me.petr1furious.potatominigames.core.queue.QueueService;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamColor;

import static me.petr1furious.potatominigames.core.command.GameCommands.requirePlayer;
import static me.petr1furious.potatominigames.core.command.GameCommands.run;

/** Player commands: {@code /play} and {@code /lobby}. */
public final class PlayCommand {
    /** {@code /play team auto} drops the pick. */
    private static final String AUTO_TEAM = "auto";

    private final SessionManager sessions;
    private final QueueService queues;
    private final LobbyService lobby;
    private final QueueMenu menu;

    public PlayCommand(SessionManager sessions, QueueService queues, LobbyService lobby, QueueMenu menu) {
        this.sessions = sessions;
        this.queues = queues;
        this.lobby = lobby;
        this.menu = menu;
    }

    public LiteralCommandNode<CommandSourceStack> buildPlay() {
        var root = Commands.literal("play")
                .executes(ctx -> run(ctx, c -> {
                    if (c.getSource().getSender() instanceof Player player) {
                        menu.open(player);
                    } else {
                        sendOverview(c);
                    }
                }));

        root.then(Commands.literal("list")
                .executes(ctx -> run(ctx, this::sendOverview)));

        root.then(Commands.literal("leave")
                .executes(ctx -> run(ctx, c -> queues.leave(requirePlayer(c)))));
        root.then(Commands.literal("vote")
                .executes(ctx -> run(ctx, c -> queues.vote(requirePlayer(c)))));
        root.then(Commands.literal("team")
                .executes(ctx -> run(ctx, c -> {
                    Player player = requirePlayer(c);
                    QueueService.Queue queue = queues.requireTeamQueue(player);
                    if (queue.teamColors().isEmpty()) {
                        throw new CommandFailure("No team colours are available in this queue.");
                    }
                    menu.openTeams(player, queue);
                }))
                .then(Commands.argument("colour", StringArgumentType.word())
                        .suggests((context, builder) -> Suggest.strings(teamOptions(context.getSource()))
                                .getSuggestions(context, builder))
                        .executes(ctx -> run(ctx, c -> {
                            String raw = c.getArgument("colour", String.class);
                            if (raw.equalsIgnoreCase(AUTO_TEAM)) {
                                queues.clearTeamPick(requirePlayer(c));
                                return;
                            }
                            TeamColor color = TeamColor.fromId(raw);
                            if (color == null) {
                                throw new CommandFailure("Unknown team colour: " + raw);
                            }
                            queues.pickTeam(requirePlayer(c), color);
                        }))));
        root.then(Commands.literal("difficulty")
                .executes(ctx -> run(ctx, c -> {
                    Player player = requirePlayer(c);
                    menu.openDifficulties(player, queues.requireDifficultyQueue(player));
                }))
                .then(Commands.argument("difficulty", StringArgumentType.word())
                        .suggests((context, builder) -> Suggest.strings(difficultyOptions(context.getSource()))
                                .getSuggestions(context, builder))
                        .executes(ctx -> run(ctx, c -> {
                            String raw = c.getArgument("difficulty", String.class);
                            queues.pickDifficulty(requirePlayer(c),
                                    raw.equalsIgnoreCase(QueueService.ANY_DIFFICULTY) ? null : raw);
                        }))));
        root.then(Commands.argument("game", StringArgumentType.word())
                .suggests(Suggest.dynamic(this::gameIds))
                .executes(ctx -> run(ctx, c -> joinGame(c, null, null)))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> Suggest.strings(
                                modeIds(context.getArgument("game", String.class))).getSuggestions(context, builder))
                        .executes(ctx -> run(ctx, c -> joinGame(c, c.getArgument("mode", String.class), null)))
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .suggests((context, builder) -> Suggest.strings(
                                        gameDifficulties(context.getArgument("game", String.class)))
                                        .getSuggestions(context, builder))
                                .executes(ctx -> run(ctx, c -> joinGame(c, c.getArgument("mode", String.class),
                                        c.getArgument("difficulty", String.class)))))));
        return root.build();
    }

    public LiteralCommandNode<CommandSourceStack> buildLobby() {
        return Commands.literal("lobby")
                .executes(ctx -> run(ctx, c -> {
                    Player player = requirePlayer(c);
                    if (lobby.world() == null) {
                        throw new CommandFailure("There is no lobby on this server.");
                    }
                    Optional<ActiveSession> session = sessions.sessionOf(player);
                    if (session.isPresent()) {
                        // Leaving already sends them back.
                        session.get().leave(player);
                        if (lobby.isLobby(player.getWorld())) {
                            return;
                        }
                    }
                    if (!lobby.isLobby(player.getWorld())) {
                        lobby.sendToLobby(player);
                    }
                }))
                .build();
    }

    /** The colours of the sender's queue, or all of them outside one. */
    private List<String> teamOptions(CommandSourceStack source) {
        List<TeamColor> colours = source.getSender() instanceof Player player
                ? queues.queueOf(player).map(QueueService.Queue::teamColors).orElse(List.of())
                : List.of();
        List<String> ids = new ArrayList<>();
        for (TeamColor color : colours.isEmpty() ? List.of(TeamColor.values()) : colours) {
            ids.add(color.id());
        }
        ids.add(AUTO_TEAM);
        return ids;
    }

    /** The levels of the sender's queue, or of all games outside one. */
    private List<String> difficultyOptions(CommandSourceStack source) {
        List<String> ids = new ArrayList<>(source.getSender() instanceof Player player
                ? queues.queueOf(player).map(QueueService.Queue::difficulties).orElse(List.of())
                : List.of());
        ids.add(QueueService.ANY_DIFFICULTY);
        return ids;
    }

    private List<String> gameDifficulties(String gameId) {
        Minigame game = sessions.game(gameId);
        if (game == null || game.difficulties().isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(game.difficulties());
        ids.add(QueueService.ANY_DIFFICULTY);
        return ids;
    }

    private List<String> gameIds() {
        List<String> ids = new ArrayList<>();
        for (QueueService.Queue queue : queues.visibleQueues()) {
            if (!ids.contains(queue.game().id())) {
                ids.add(queue.game().id());
            }
        }
        return ids;
    }

    private List<String> modeIds(String gameId) {
        Minigame game = sessions.game(gameId);
        if (game == null || game.modes().size() <= 1) {
            return List.of();
        }
        return queues.queuesOf(game).stream().map(QueueService.Queue::mode).toList();
    }

    /** Then opens the difficulty and team menus, skipping a difficulty given here. */
    private void joinGame(CommandContext<CommandSourceStack> ctx, String mode, String difficulty)
            throws CommandFailure {
        Player player = requirePlayer(ctx);
        String gameId = ctx.getArgument("game", String.class);
        Minigame game = sessions.game(gameId);
        if (game == null) {
            throw new CommandFailure("Unknown game: " + gameId);
        }
        List<QueueService.Queue> available = queues.queuesOf(game);
        if (available.isEmpty()) {
            throw new CommandFailure(game.id() + " has no open queues.");
        }
        QueueService.Queue queue;
        if (mode == null) {
            if (available.size() > 1 && sessions.sessionOf(game).isEmpty()) {
                menu.open(player, game);
                return;
            }
            queue = available.getFirst();
        } else {
            queue = available.stream().filter(candidate -> candidate.mode().equalsIgnoreCase(mode)).findFirst()
                    .orElseThrow(() -> new CommandFailure("Unknown or closed mode: " + mode));
        }
        if (difficulty != null && !difficulty.equalsIgnoreCase(QueueService.ANY_DIFFICULTY)
                && !queue.difficulties().contains(difficulty.toLowerCase(Locale.ROOT))) {
            throw new CommandFailure(queue.difficulties().isEmpty() ? game.id() + " has no difficulty levels."
                    : "Unknown difficulty: " + difficulty + " (options: " + String.join(", ", queue.difficulties())
                            + ", " + QueueService.ANY_DIFFICULTY + ")");
        }
        // Already queued: just the picks.
        if (!queue.contains(player.getUniqueId())) {
            queues.join(player, queue);
            if (!queue.contains(player.getUniqueId())) {
                return;
            }
        }
        if (difficulty == null) {
            menu.openPicks(player, queue);
            return;
        }
        String vote = difficulty.equalsIgnoreCase(QueueService.ANY_DIFFICULTY) ? null
                : difficulty.toLowerCase(Locale.ROOT);
        if (!Objects.equals(queue.difficultyVote(player.getUniqueId()), vote)) {
            queues.pickDifficulty(player, vote);
        }
        if (!queue.teamColors().isEmpty()) {
            menu.openTeams(player, queue);
        }
    }

    private void sendOverview(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text("Games:", NamedTextColor.GOLD));
        for (QueueService.Queue queue : queues.visibleQueues()) {
            sender.sendMessage(line(queue));
        }
        if (sender instanceof Player player) {
            queues.queueOf(player).ifPresent(queue -> player.sendMessage(Component.text()
                    .append(Component.text("You are queued for ", NamedTextColor.GRAY))
                    .append(queue.displayName())
                    .append(Component.text(". ", NamedTextColor.GRAY))
                    .append(button("Leave", "/play leave", "Leave the queue"))
                    .build()));
        }
    }

    private Component line(QueueService.Queue queue) {
        Optional<ActiveSession> running = sessions.sessionOf(queue.game());
        String status;
        if (running.isPresent()) {
            status = "running, click to spectate";
        } else if (queue.countdown() != null) {
            status = queue.countText() + " queued, starts in " + queue.countdown() + "s";
        } else {
            status = queue.countText() + " queued, needs " + queue.minPlayers();
        }
        Component description = queue.game().description(queue.mode());
        return Component.text()
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(queue.displayName())
                .append(Component.text(" — " + status, NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand(queue.command()))
                .hoverEvent(HoverEvent.showText(description.equals(Component.empty())
                        ? Component.text(queue.command(), NamedTextColor.GRAY)
                        : description.colorIfAbsent(NamedTextColor.GRAY)))
                .build();
    }

    private static Component button(String label, String command, String hover) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }
}
