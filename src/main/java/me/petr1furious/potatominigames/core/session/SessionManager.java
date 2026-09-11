package me.petr1furious.potatominigames.core.session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.hud.BossBarService;
import me.petr1furious.potatominigames.core.hud.SidebarService;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.score.PointsService;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.world.ArenaService;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.core.world.SpawnFinder;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;

/**
 * The single-active-session state machine: IDLE → RUNNING → ENDING → IDLE.
 * Owns every task handle and guarantees the plugin is inert while IDLE.
 *
 * <p>There is deliberately no lobby phase: {@code start} takes a selector (or
 * everyone online) and begins immediately, and {@code join} is a late-join into a
 * game already in progress. Teams are arranged beforehand and live in
 * {@link me.petr1furious.potatominigames.core.team.TeamRegistry}.
 */
public final class SessionManager {
    private final JavaPlugin plugin;
    private final PlayerStateService playerState;
    private final TeamService teams;
    private final SidebarService sidebar;
    private final BossBarService bossBars;
    private final ArenaService arena;
    private final SlimeWorldService slimeWorlds;
    private final ThrowawayWorldService throwawayWorlds;
    private final SpawnFinder spawnFinder;
    private final Messages messages;
    private final PointsService points;

    private final Map<String, Minigame> games = new LinkedHashMap<>();
    private final List<Runnable> cleanupHooks = new java.util.ArrayList<>();

    private final PropertyBag coreConfig = new PropertyBag("core");
    private final Property<Boolean> enableDummyGame = coreConfig.define("enableDummyGame", PropertyCodec.BOOL, false);

    public boolean isDummyGameEnabled() {
        return enableDummyGame.get();
    }

    private GamePhase phase = GamePhase.IDLE;
    private Minigame activeGame;
    private GameSession session;
    private ParticipantRegistry participants;
    private BukkitTask ticker;
    private BukkitTask endingTask;
    private long elapsedSeconds;

    public SessionManager(JavaPlugin plugin, PlayerStateService playerState, TeamService teams,
            SidebarService sidebar, BossBarService bossBars, ArenaService arena, SlimeWorldService slimeWorlds,
            ThrowawayWorldService throwawayWorlds, SpawnFinder spawnFinder, Messages messages,
            PointsService points) {
        this.plugin = plugin;
        this.playerState = playerState;
        this.teams = teams;
        this.sidebar = sidebar;
        this.bossBars = bossBars;
        this.arena = arena;
        this.slimeWorlds = slimeWorlds;
        this.throwawayWorlds = throwawayWorlds;
        this.spawnFinder = spawnFinder;
        this.messages = messages;
        this.points = points;
    }

    public PropertyBag coreConfig() {
        return coreConfig;
    }

    public void registerGame(Minigame game) {
        games.put(game.id(), game);
    }

    /** Runs on every session cleanup (e.g. despawning gadget entities). */
    public void addCleanupHook(Runnable hook) {
        cleanupHooks.add(hook);
    }

    public List<Minigame> games() {
        return List.copyOf(games.values());
    }

    public GamePhase phase() {
        return phase;
    }

    public Optional<Minigame> activeGame() {
        return Optional.ofNullable(activeGame);
    }

    public Optional<GameSession> activeSession() {
        return Optional.ofNullable(session);
    }

    public ParticipantRegistry participants() {
        return participants;
    }

    public long elapsedSeconds() {
        return elapsedSeconds;
    }

    public boolean isRunning() {
        return phase == GamePhase.RUNNING;
    }

    public boolean isSessionOf(Minigame game) {
        return activeGame == game;
    }

    public boolean isParticipant(Player player) {
        return participants != null && participants.isParticipant(player);
    }

    // --- lifecycle ---

    /**
     * Starts a game immediately with the given players, or everyone online when no
     * selector was supplied.
     */
    public void start(Minigame game, List<Player> selected) throws CommandFailure {
        if (phase != GamePhase.IDLE) {
            throw new CommandFailure(Component.text()
                    .append(activeGame.displayName())
                    .append(Component.text(" is already " + phase.name().toLowerCase() + ".", NamedTextColor.RED))
                    .build());
        }
        List<Player> roster = selected == null || selected.isEmpty()
                ? List.copyOf(plugin.getServer().getOnlinePlayers())
                : selected;
        if (roster.isEmpty()) {
            throw new CommandFailure("No players to start the game with.");
        }

        participants = new ParticipantRegistry();
        teams.attach(participants);
        activeGame = game;
        session = game.createSession(new SessionServices(plugin, this, participants, playerState, teams,
                sidebar, bossBars, arena, slimeWorlds, throwawayWorlds, spawnFinder, messages, points));

        for (Player player : roster) {
            Participant participant = participants.add(player);
            if (game.usesTeams()) {
                teams.resolveForStart(game.id(), participant);
            }
            playerState.snapshot(player);
        }

        phase = GamePhase.RUNNING;
        elapsedSeconds = 0;
        try {
            session.onStart();
        } catch (CommandFailure failure) {
            // Put everyone back and tear the half-built session down completely.
            cleanup();
            throw failure;
        }

        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            elapsedSeconds++;
            session.onSecondTick(elapsedSeconds);
        }, 20L, 20L);
        messages.broadcast(Component.text()
                .append(game.displayName())
                .append(Component.text(" started with " + participants.size() + " players!", NamedTextColor.GREEN))
                .build());
        messages.playSound(participants.onlinePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /**
     * Ends the running game and starts a fresh one right away — with the same
     * players unless a selector is given. Works as a plain start when idle, so it
     * doubles as a "go again" button.
     */
    public void restart(Minigame game, List<Player> selected) throws CommandFailure {
        List<Player> roster = selected;
        if (phase != GamePhase.IDLE) {
            if (!isSessionOf(game)) {
                throw new CommandFailure(Component.text()
                        .append(activeGame.displayName())
                        .append(Component.text(" is active — stop it first.", NamedTextColor.RED))
                        .build());
            }
            if (roster == null || roster.isEmpty()) {
                roster = participants.onlinePlayers();
            }
            stopNow(EndReason.ABORTED);
        }
        start(game, roster);
    }

    /**
     * Late-joins a player into a game already in progress, or puts a participant who
     * was knocked out back into it.
     */
    public void join(Player player) throws CommandFailure {
        if (phase != GamePhase.RUNNING) {
            throw new CommandFailure("No game is running.");
        }
        boolean returning = participants.isParticipant(player);
        if (returning && session.isInGame(player)) {
            throw new CommandFailure(player.getName() + " is already in the game.");
        }
        if (!session.allowLateJoin()) {
            throw new CommandFailure(player.getName() + " cannot join this game once it has started.");
        }
        // A returning player already has their pre-game snapshot; taking another now
        // would record them as a spectator and "restore" that at the end.
        if (!returning) {
            Participant participant = participants.add(player);
            if (activeGame.usesTeams()) {
                teams.resolveForStart(activeGame.id(), participant);
            }
            playerState.snapshot(player);
        }
        session.onParticipantJoin(player);
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" joined the game", NamedTextColor.GREEN))
                .build());
    }

    public void leave(Player player) throws CommandFailure {
        if (phase == GamePhase.IDLE) {
            throw new CommandFailure("No game is running.");
        }
        if (!participants.isParticipant(player)) {
            throw new CommandFailure(player.getName() + " is not in the game.");
        }
        if (phase == GamePhase.RUNNING && !session.allowLeave()) {
            throw new CommandFailure("You cannot leave " + activeGame.id() + " once it has started.");
        }
        if (phase == GamePhase.RUNNING || phase == GamePhase.ENDING) {
            sidebar.clear(player);
            playerState.restore(player);
        }
        participants.remove(player.getUniqueId());
        session.onParticipantLeave(player);
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" left the game", NamedTextColor.YELLOW))
                .build());
        if (phase == GamePhase.RUNNING && participants.isEmpty()) {
            endGame(EndReason.ALL_PLAYERS_LEFT);
        }
    }

    /** Finishes the running game: announce (session.onEnd), then cleanup. */
    public void endGame(EndReason reason) {
        if (phase != GamePhase.RUNNING) {
            return;
        }
        phase = GamePhase.ENDING;
        cancelTicker();
        session.onEnd(reason);
        // Next tick rather than inline: endGame is reached from inside a death handler,
        // and restoring a player there fights with the respawn screen about to open.
        endingTask = plugin.getServer().getScheduler().runTask(plugin, this::cleanup);
    }

    /** Immediate teardown from any phase (also used from onDisable). */
    public void stopNow(EndReason reason) {
        if (phase == GamePhase.IDLE) {
            return;
        }
        cancelTicker();
        if (endingTask != null) {
            endingTask.cancel();
            endingTask = null;
        }
        if (phase == GamePhase.RUNNING) {
            session.onEnd(reason);
        }
        cleanup();
    }

    private void cleanup() {
        endingTask = null;
        for (Runnable hook : cleanupHooks) {
            hook.run();
        }
        for (Player player : participants.onlinePlayers()) {
            sidebar.clear(player);
            playerState.restore(player);
        }
        sidebar.clearAll();
        bossBars.hideAll();
        arena.restoreAllBorders();
        // After players are back in their own world, the game's scratch worlds go away.
        throwawayWorlds.destroyAll();
        teams.detach();
        session = null;
        activeGame = null;
        participants = null;
        phase = GamePhase.IDLE;
    }

    // --- called by the core listener ---

    public void handleQuit(Player player) {
        if (phase == GamePhase.IDLE || !participants.isParticipant(player)) {
            return;
        }
        participants.find(player.getUniqueId()).ifPresent(participant -> participant.setOnline(false));
        session.onParticipantQuit(player);
    }

    public void handleJoin(Player player) {
        // A snapshot with no active session means the player quit mid-game and the game
        // ended without them: put them back the way they were.
        if (phase == GamePhase.IDLE || !participants.isParticipant(player)) {
            if (playerState.hasSnapshot(player.getUniqueId())) {
                sidebar.clear(player);
                playerState.restore(player);
            }
            return;
        }
        participants.find(player.getUniqueId()).ifPresent(participant -> participant.setOnline(true));
        session.onParticipantRejoin(player);
    }

    private void cancelTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }
}
