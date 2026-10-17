package me.petr1furious.potatominigames.core.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.hud.BossBarService;
import me.petr1furious.potatominigames.core.hud.SidebarService;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.core.team.TeamService;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.world.ArenaService;
import me.petr1furious.potatominigames.core.world.SessionWorlds;

/**
 * One running game: its participants, spectators, clock and everything it shows.
 * The lifecycle is RUNNING → ENDING → IDLE; once IDLE the session is discarded.
 *
 * <p>Several sessions run side by side (one per game type), so everything that
 * used to be server-wide lives here instead: the sidebar, boss bars, borders, team
 * view and an audience-scoped {@link Messages} that reaches only this game.
 */
public final class ActiveSession {
    private final JavaPlugin plugin;
    private final SessionManager manager;
    private final Minigame game;
    private final String mode;
    private final PlayerStateService playerState;
    private final LobbyService lobby;

    private final ParticipantRegistry participants = new ParticipantRegistry();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final SidebarService sidebar = new SidebarService(this::spectatorPlayers);
    private final BossBarService bossBars = new BossBarService(this::audiencePlayers);
    private final ArenaService arena = new ArenaService();
    private final Messages messages = new Messages(this::audience);
    private final TeamService teams;
    private final SessionWorlds worlds;

    private GameSession session;
    private GamePhase phase = GamePhase.IDLE;
    private BukkitTask ticker;
    private BukkitTask endingTask;
    private long elapsedSeconds;

    ActiveSession(JavaPlugin plugin, SessionManager manager, Minigame game, String mode,
            PlayerStateService playerState, LobbyService lobby, TeamService teams, SessionWorlds worlds) {
        this.plugin = plugin;
        this.manager = manager;
        this.game = game;
        this.mode = mode;
        this.playerState = playerState;
        this.lobby = lobby;
        this.teams = teams;
        this.worlds = worlds;
    }

    // --- identity and state ---

    public Minigame game() {
        return game;
    }

    public String mode() {
        return mode;
    }

    /** "Bingo" or, for games with several modes, "Bingo (butfast)". */
    public Component displayName() {
        return SessionManager.displayName(game, mode);
    }

    public GamePhase phase() {
        return phase;
    }

    public boolean isRunning() {
        return phase == GamePhase.RUNNING;
    }

    public long elapsedSeconds() {
        return elapsedSeconds;
    }

    public GameSession gameSession() {
        return session;
    }

    public ParticipantRegistry participants() {
        return participants;
    }

    public TeamService teams() {
        return teams;
    }

    public SidebarService sidebar() {
        return sidebar;
    }

    public SessionWorlds worlds() {
        return worlds;
    }

    public boolean isParticipant(Player player) {
        return participants.isParticipant(player);
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public boolean owns(World world) {
        return worlds.owns(world);
    }

    public List<Player> spectatorPlayers() {
        List<Player> result = new ArrayList<>();
        for (UUID id : spectators) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    /** Everyone who should hear this game: online participants and spectators. */
    public List<Player> audiencePlayers() {
        List<Player> result = new ArrayList<>(participants.onlinePlayers());
        result.addAll(spectatorPlayers());
        return result;
    }

    private Audience audience() {
        List<Audience> members = new ArrayList<>(audiencePlayers());
        // The console keeps a record of every game, as it did when all chat was global.
        members.add(Bukkit.getConsoleSender());
        return Audience.audience(members);
    }

    // --- lifecycle ---

    /**
     * Starts with the given roster. Queue picks decide teams for queued games;
     * games an admin started directly use the saved colours instead.
     */
    void start(List<Player> roster, Map<UUID, TeamColor> teamPicks, boolean queued) throws CommandFailure {
        // Running from the first step, so that a failure anywhere below is cleaned up.
        phase = GamePhase.RUNNING;
        elapsedSeconds = 0;
        try {
            teams.configureStart(teamPicks, !queued);
            teams.attach(participants);
            session = game.createSession(new SessionServices(plugin, this, participants, playerState, teams,
                    sidebar, bossBars, arena, worlds, manager.spawnFinder(), messages, manager.points()), mode);

            for (Player player : roster) {
                Participant participant = participants.add(player);
                if (game.usesTeams()) {
                    teams.resolveForStart(game.id(), participant);
                }
                lobby.prepareForGame(player);
                manager.index(player, this);
            }
            session.onStart();
        } catch (CommandFailure | RuntimeException failure) {
            // Tear the half-built session down completely. Queued players stay queued, so
            // they wait in the lobby again; everyone else is put back.
            cleanup(false, queued);
            if (failure instanceof CommandFailure commandFailure) {
                throw commandFailure;
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not start " + game.id(), failure);
            throw new CommandFailure("The game failed to start: " + failure.getMessage());
        }

        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            elapsedSeconds++;
            session.onSecondTick(elapsedSeconds);
        }, 20L, 20L);
        messages.broadcastGlobal(Component.text()
                .append(displayName())
                .append(Component.text(" started with " + Messages.count(participants.size(), "player") + "!", NamedTextColor.GREEN))
                .build());
        messages.playSound(participants.onlinePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /**
     * Late-joins a player into a game already in progress, or puts a participant who
     * was knocked out back into it. Admin-only: players spectate instead.
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
        if (isSpectator(player)) {
            stopSpectating(player, false);
        }
        // A returning player already has their pre-game snapshot; taking another now
        // would record them as a spectator and "restore" that at the end.
        if (!returning) {
            Participant participant = participants.add(player);
            if (game.usesTeams()) {
                teams.resolveForStart(game.id(), participant);
            }
            lobby.prepareForGame(player);
            manager.index(player, this);
        }
        session.onParticipantJoin(player);
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" joined the game", NamedTextColor.GREEN))
                .build());
    }

    public void leave(Player player) throws CommandFailure {
        if (isSpectator(player)) {
            stopSpectating(player, true);
            return;
        }
        if (!participants.isParticipant(player)) {
            throw new CommandFailure(player.getName() + " is not in the game.");
        }
        if (phase == GamePhase.RUNNING && !session.allowLeave() && session.isInGame(player)) {
            throw new CommandFailure("You cannot leave " + game.id() + " once it has started.");
        }
        sidebar.clear(player);
        bossBars.hideFrom(player);
        participants.remove(player.getUniqueId());
        manager.unindex(player.getUniqueId(), this);
        session.onParticipantLeave(player);
        lobby.returnPlayer(player);
        messages.broadcast(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" left the game", NamedTextColor.YELLOW))
                .build());
        if (phase == GamePhase.RUNNING && participants.isEmpty()) {
            endGame(EndReason.ALL_PLAYERS_LEFT);
        }
    }

    // --- spectators ---

    /** Puts a player into this game as a spectator, wherever they are. */
    public void spectate(Player player) throws CommandFailure {
        if (phase == GamePhase.IDLE) {
            throw new CommandFailure("That game is not running.");
        }
        if (participants.isParticipant(player)) {
            throw new CommandFailure("You are playing in this game.");
        }
        if (!spectators.add(player.getUniqueId())) {
            return;
        }
        lobby.prepareForGame(player);
        manager.index(player, this);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(spectatorLocation(player));
        bossBars.sync(player);
        player.sendMessage(Component.text()
                .append(Component.text("You are spectating ", NamedTextColor.GRAY))
                .append(displayName())
                .append(Component.text(". ", NamedTextColor.GRAY))
                .append(button("Leave", "/play leave", "Stop spectating"))
                .build());
    }

    /** Stops spectating without sending them anywhere, for a player who is moving on to something else. */
    public void dropSpectator(Player player) {
        stopSpectating(player, false);
        // Admins skip the lobby state that would otherwise take them out of spectator mode.
        if (player.getGameMode() == GameMode.SPECTATOR && LobbyService.isExempt(player)) {
            GameMode before = playerState.snapshotGameMode(player.getUniqueId());
            player.setGameMode(before != null && before != GameMode.SPECTATOR ? before : GameMode.SURVIVAL);
        }
    }

    void stopSpectating(Player player, boolean sendBack) {
        if (!spectators.remove(player.getUniqueId())) {
            return;
        }
        manager.unindex(player.getUniqueId(), this);
        sidebar.clear(player);
        bossBars.hideFrom(player);
        if (sendBack) {
            lobby.returnPlayer(player);
        }
    }

    private Location spectatorLocation(Player viewer) {
        Location preferred = session.spectatorLocation(viewer);
        if (preferred != null) {
            return preferred;
        }
        List<Player> playing = participants.onlinePlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .toList();
        if (!playing.isEmpty()) {
            return playing.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(playing.size()))
                    .getLocation();
        }
        for (Player participant : participants.onlinePlayers()) {
            return participant.getLocation();
        }
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    // --- ending ---

    /** Finishes the running game: announce (session.onEnd), then cleanup. */
    public void endGame(EndReason reason) {
        if (phase != GamePhase.RUNNING) {
            return;
        }
        phase = GamePhase.ENDING;
        cancelTicker();
        session.onEnd(reason);
        announceResult();
        // Next tick rather than inline: endGame is reached from inside a death handler,
        // and restoring a player there fights with the respawn screen about to open.
        endingTask = plugin.getServer().getScheduler().runTask(plugin, () -> cleanup(true, false));
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
            // ENDING first: a game that ends itself from onEnd must not schedule a second cleanup.
            phase = GamePhase.ENDING;
            session.onEnd(reason);
        }
        cleanup(false, false);
    }

    private void announceResult() {
        Component result = session.globalResult();
        messages.broadcastGlobal(result != null ? result
                : Component.text().append(displayName())
                        .append(Component.text(" has ended.", NamedTextColor.GRAY)).build());
    }

    private void cleanup(boolean offerReplay, boolean backToQueue) {
        if (phase == GamePhase.IDLE) {
            return;
        }
        if (endingTask != null) {
            endingTask.cancel();
            endingTask = null;
        }
        cancelTicker();
        List<Player> returning = audiencePlayers();
        Set<UUID> involved = new LinkedHashSet<>();
        for (Participant participant : participants.all()) {
            involved.add(participant.id());
        }
        involved.addAll(spectators);
        manager.runCleanupHooks(involved);

        for (Player player : returning) {
            sidebar.clear(player);
            bossBars.hideFrom(player);
            if (backToQueue) {
                lobby.backToQueue(player);
            } else {
                lobby.returnPlayer(player);
            }
        }
        sidebar.clearAll();
        bossBars.hideAll();
        arena.restoreAllBorders();
        // After players are back in their own world, the game's scratch worlds go away.
        worlds.destroyOwn();
        teams.detach();
        phase = GamePhase.IDLE;
        manager.onSessionEnded(this, involved);
        participants.clear();
        spectators.clear();

        if (offerReplay) {
            String command = manager.playCommand(game, mode);
            for (Player player : returning) {
                if (player.isOnline()) {
                    player.sendMessage(Component.text()
                            .append(Component.text("Thanks for playing! ", NamedTextColor.GRAY))
                            .append(button("Play again", command, "Queue for another round"))
                            .build());
                }
            }
        }
    }

    // --- connection changes, called by the manager ---

    void handleQuit(Player player) {
        if (spectators.contains(player.getUniqueId())) {
            // Their snapshot stays saved; they are sent back when they reconnect.
            spectators.remove(player.getUniqueId());
            manager.unindex(player.getUniqueId(), this);
            sidebar.clear(player);
            return;
        }
        if (!participants.isParticipant(player)) {
            return;
        }
        participants.find(player.getUniqueId()).ifPresent(participant -> participant.setOnline(false));
        if (phase != GamePhase.IDLE) {
            session.onParticipantQuit(player);
        }
    }

    void handleRejoin(Player player) {
        participants.find(player.getUniqueId()).ifPresent(participant -> participant.setOnline(true));
        session.onParticipantRejoin(player);
    }

    private void cancelTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    static Component button(String label, String command, String hover) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    /** The spectators' ids, for status output. */
    public Collection<UUID> spectatorIds() {
        return List.copyOf(spectators);
    }
}
