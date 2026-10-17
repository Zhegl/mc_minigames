package me.petr1furious.potatominigames.core.command;

import java.util.ArrayList;
import java.util.List;
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
import me.petr1furious.potatominigames.core.queue.QueueService;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamColor;

import static me.petr1furious.potatominigames.core.command.GameCommands.requirePlayer;
import static me.petr1furious.potatominigames.core.command.GameCommands.run;

/**
 * The commands every player has: {@code /play} to see and join queues (and leave,
 * vote, pick a team), and {@code /lobby} to get back to the lobby from anywhere.
 */
public final class PlayCommand {
    private final SessionManager sessions;
    private final QueueService queues;
    private final LobbyService lobby;

    public PlayCommand(SessionManager sessions, QueueService queues, LobbyService lobby) {
        this.sessions = sessions;
        this.queues = queues;
        this.lobby = lobby;
    }

    public LiteralCommandNode<CommandSourceStack> buildPlay() {
        var root = Commands.literal("play")
                .executes(ctx -> run(ctx, this::sendOverview));

        root.then(Commands.literal("leave")
                .executes(ctx -> run(ctx, c -> queues.leave(requirePlayer(c)))));
        root.then(Commands.literal("vote")
                .executes(ctx -> run(ctx, c -> queues.vote(requirePlayer(c)))));
        root.then(Commands.literal("team")
                .then(Commands.argument("colour", StringArgumentType.word())
                        .suggests(Suggest.strings(java.util.Arrays.stream(TeamColor.values())
                                .map(TeamColor::id).toList()))
                        .executes(ctx -> run(ctx, c -> {
                            String raw = c.getArgument("colour", String.class);
                            TeamColor color = TeamColor.fromId(raw);
                            if (color == null) {
                                throw new CommandFailure("Unknown team colour: " + raw);
                            }
                            queues.pickTeam(requirePlayer(c), color);
                        }))));
        root.then(Commands.argument("game", StringArgumentType.word())
                .suggests(Suggest.dynamic(this::gameIds))
                .executes(ctx -> run(ctx, c -> joinGame(c, null)))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> Suggest.strings(
                                modeIds(context.getArgument("game", String.class))).getSuggestions(context, builder))
                        .executes(ctx -> run(ctx, c -> joinGame(c, c.getArgument("mode", String.class))))));
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
                        // Leaving a game or spectating already takes them back where they belong.
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

    private void joinGame(CommandContext<CommandSourceStack> ctx, String mode) throws CommandFailure {
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
                // Several modes and nothing to spectate: let them pick.
                sendModes(player, game, available);
                return;
            }
            queue = available.getFirst();
        } else {
            queue = available.stream().filter(candidate -> candidate.mode().equalsIgnoreCase(mode)).findFirst()
                    .orElseThrow(() -> new CommandFailure("Unknown or closed mode: " + mode));
        }
        queues.join(player, queue);
    }

    private void sendModes(Player player, Minigame game, List<QueueService.Queue> available) {
        player.sendMessage(Component.text().append(game.displayName())
                .append(Component.text(" — pick a mode:", NamedTextColor.GRAY)).build());
        for (QueueService.Queue queue : available) {
            player.sendMessage(line(queue));
        }
    }

    private void sendOverview(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text("Games — click one to join:", NamedTextColor.GOLD));
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

    /** One clickable line per queue: its name, what it is about and how full it is. */
    private Component line(QueueService.Queue queue) {
        Optional<ActiveSession> running = sessions.sessionOf(queue.game());
        String status;
        if (running.isPresent()) {
            status = "in progress, click to spectate";
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
